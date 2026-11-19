package dev.dynamicchunkie.benchmark;
import dev.dynamicchunkie.*;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import java.nio.file.*;
import java.util.*;
/** Explicit A/B harness. Normal client tests skip it; Gradle -Pbenchmark selects only this entrypoint. */
public final class HotSoftBenchmark implements FabricClientGameTest {
    private record Phase(String scene,int repeat,boolean hotSoft,FrameCapture.Result measurement) {}
    @Override public void runTest(ClientGameTestContext context) {
        if(!Boolean.getBoolean("dynamicchunkie.benchmark"))return;
        int warmup=Integer.getInteger("dynamicchunkie.benchmarkWarmupTicks",100);
        int samples=Integer.getInteger("dynamicchunkie.benchmarkSampleTicks",200);
        int repeats=Integer.getInteger("dynamicchunkie.benchmarkRepeats",2);
        if(warmup<10 || samples<20 || repeats<1 || repeats>10)throw new IllegalArgumentException("Invalid benchmark duration/repeats");
        var backup=context.computeOnClient(client->DynamicChunkie.config().copy());
        int fpsLimit=context.computeOnClient(client->client.options.framerateLimit().get());
        var inactivity=context.computeOnClient(client->client.options.inactivityFpsLimit().get());
        boolean pauseOnLostFocus=context.computeOnClient(client->client.options.pauseOnLostFocus);
        boolean vsync=context.computeOnClient(client->client.options.enableVsync().get());
        int simulation=context.computeOnClient(client->client.options.simulationDistance().get());
        var graphics=context.computeOnClient(client->Map.of(
                "backend",com.mojang.blaze3d.systems.RenderSystem.getBackendDescription(),
                "width",client.getWindow().getWidth(),"height",client.getWindow().getHeight()));
        var phases=new ArrayList<Phase>();
        try(var world=context.worldBuilder().create()) {
            world.getServer().runCommand("difficulty peaceful");world.getServer().runCommand("weather clear");
            world.getServer().runCommand("time set noon");world.getServer().runCommand("gamemode creative @a");
            world.getServer().runCommand("fill -64 95 -64 63 95 63 minecraft:stone");
            world.getServer().runCommand("fill 16 96 -16 31 127 -1 minecraft:stone");
            world.getServer().runCommand("fill -48 96 0 -33 111 15 minecraft:gold_block");
            world.getServer().runCommand("fill -16 111 -16 15 111 15 minecraft:stone");
            context.runOnClient(client->{client.options.enableVsync().set(false);client.options.framerateLimit().set(260);
                client.options.inactivityFpsLimit().set(net.minecraft.client.InactivityFpsLimit.MINIMIZED);client.options.pauseOnLostFocus=false;});
            for(String scene:List.of("top_down_flight","horizon","cave","offscreen_updates"))for(int repeat=0;repeat<repeats;repeat++) {
                for(int order=0;order<2;order++) {
                    boolean on=((repeat+order)&1)==1;
                    context.runOnClient(client->{
                        var c=backup.copy();c.enabled=true;c.minRenderDistance=8;c.maxRenderDistance=8;
                        c.preserveRenderCache=true;c.enableHotSoftSections=on;c.hotSoftScanBudgetMicros=5000;
                        c.enableTerrainSectionCulling=false;c.enableEntityCulling=false;c.enableAdaptiveEntityDistance=false;
                        c.enableEntityTypeLimits=false;c.enableAdaptiveParticles=false;c.enableSmartClouds=false;
                        c.enableFpsAdaptive=false;c.enableHardwareAdaptive=false;c.enableTeleportRecovery=false;
                        c.enableAdaptiveAnalysis=false;c.analysisIntervalMs=10000;c.profilerOverlay=false;c.debugMode=false;
                        if(!DynamicChunkie.applyConfig(c))throw new AssertionError("Cannot apply benchmark settings");
                    });
                    double y=scene.equals("cave") || scene.equals("offscreen_updates")?100:160;float pitch=scene.equals("top_down_flight")?90:0;
                    world.getServer().runCommand("tp @a 0.5 "+y+" 0.5 -90 "+pitch);
                    context.waitTicks(5);
                    context.runOnClient(client->{client.player.getAbilities().flying=true;client.player.onUpdateAbilities();});
                    if(scene.equals("offscreen_updates")) {
                        world.getServer().runCommand("tp @a 0.5 100 0.5 90 0");
                        context.waitTicks(20);world.getConnection().waitForChunksRender(false);
                        world.getServer().runCommand("tp @a 0.5 100 0.5 -90 0");
                    }
                    world.getConnection().waitForChunksRender(false);
                    for(int tick=0;tick<warmup;tick++){step(context,scene,tick);context.waitTick();}
                    context.runOnClient(client->FrameCapture.begin());
                    for(int tick=0;tick<samples;tick++){
                        step(context,scene,tick);
                        if(scene.equals("offscreen_updates") && tick%5==0)
                            world.getServer().runCommand("setblock -40 100 8 minecraft:"+(tick%10==0?"stone":"gold_block"));
                        context.waitTick();
                    }
                    var result=context.computeOnClient(client->FrameCapture.end());
                    if(result.frames<10)throw new AssertionError("Too few rendered benchmark frames");
                    if(on && result.activeFrames==0)throw new AssertionError("Hot/soft never became active; comparison invalid");
                    phases.add(new Phase(scene,repeat,on,result));
                    DynamicChunkie.LOG.info("Benchmark {} {}: {} FPS, p95={}ms, extra skipped={}",scene,on?"hot-soft":"baseline",result.fps,result.p95Ms,result.meanSkipped);
                }
            }
        }finally {
            context.runOnClient(client->{FrameCapture.cancel();DynamicChunkie.applyConfig(backup);
                client.options.framerateLimit().set(fpsLimit);client.options.enableVsync().set(vsync);
                client.options.inactivityFpsLimit().set(inactivity);client.options.pauseOnLostFocus=pauseOnLostFocus;client.options.save();
                if(client.options.simulationDistance().get()!=simulation)throw new AssertionError("Simulation Distance changed");});
        }
        try {
            String renderer=net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("sodium")?"sodium":"vanilla";
            Path directory=Path.of(System.getProperty("dynamicchunkie.benchmarkOutput","../../benchmarks"));Files.createDirectories(directory);
            String name="hot-soft-"+renderer+"-"+System.currentTimeMillis();
            var report=new LinkedHashMap<String,Object>();report.put("minecraft","26.3");report.put("modVersion","0.1.0");
            report.put("renderer",renderer);report.put("java",System.getProperty("java.version"));report.put("os",System.getProperty("os.name"));
            report.put("processors",Runtime.getRuntime().availableProcessors());report.put("warmupTicks",warmup);report.put("sampleTicks",samples);report.put("repeats",repeats);
            report.put("inactivityFpsLimit","MINIMIZED");report.put("throttleGuard",true);
            report.put("graphics",graphics);report.put("scanBudgetMicros",5000);report.put("equalCandidateTelemetry",true);
            report.put("renderDistance",8);report.put("vsync",false);report.put("framerateOption",260);
            report.put("notes",List.of("Frame intervals use System.nanoTime, not simulated game time.","Heap includes integrated server and GC; heap differences are not causal renderer memory savings.","Cold meshes/chunk data are retained; geometry evictions and chunk-data evictions are zero.","Soft slots include air/unloaded coordinates. GPU VRAM is not measured.","Short smoke runs validate the harness; use repeated warmed runs for performance conclusions."));
            report.put("phases",phases);
            Files.writeString(directory.resolve(name+".json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report));
            var csv=new StringBuilder("scene,repeat,hot_soft,frame,interval_ms\n");
            for(var phase:phases)for(int i=0;i<phase.measurement.intervals.length;i++)
                csv.append(phase.scene).append(',').append(phase.repeat).append(',').append(phase.hotSoft).append(',').append(i).append(',').append(phase.measurement.intervals[i]).append('\n');
            Files.writeString(directory.resolve(name+".csv"),csv);
            DynamicChunkie.LOG.info("Benchmark reports: {}",directory.toAbsolutePath());
        }catch(java.io.IOException e){throw new RuntimeException("Cannot write benchmark report",e);}
    }
    private void step(ClientGameTestContext context,String scene,int tick) {
        context.runOnClient(client->{
            var reason=client.getFramerateLimitTracker().getThrottleReason();
            if(reason!=com.mojang.blaze3d.platform.FramerateLimitTracker.FramerateThrottleReason.NONE)
                throw new AssertionError("Benchmark FPS throttled: "+reason+"; keep the window unminimized");
            if(!scene.equals("top_down_flight"))return;
            double x=.5+Math.sin(tick/40.0)*20;client.player.setPos(x,160,.5);
            client.player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);client.player.setYRot(-90);client.player.setXRot(90);});
    }
}
