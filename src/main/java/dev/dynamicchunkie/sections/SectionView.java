package dev.dynamicchunkie.sections;

import net.minecraft.client.Minecraft;
import net.minecraft.client.CameraType;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Exact projection and camera state used to collect a native visibility list. */
final class SectionView {
    private final Matrix4f projection = new Matrix4f();
    private final Matrix4f scratch = new Matrix4f();
    private Vec3 position;
    private float pitch, yaw, fov;
    private int sectionX,sectionY,sectionZ;
    private CameraType mode;
    private boolean panoramic;

    void capture(Minecraft client) {
        var camera = client.gameRenderer.mainCamera();
        position = camera.position();
        sectionX=camera.blockPosition().getX()>>4;
        sectionY=camera.blockPosition().getY()>>4;
        sectionZ=camera.blockPosition().getZ()>>4;
        pitch = camera.xRot();
        yaw = camera.yRot();
        fov = camera.getFov();
        mode = client.options.getCameraType();
        panoramic = camera.isPanoramicMode();
        camera.getViewRotationProjectionMatrix(projection);
    }

    boolean matches(Minecraft client, double margin) {
        var camera = client.gameRenderer.mainCamera();
        return position!=null && camera.position().distanceToSqr(position)<=margin*margin && pitch == camera.xRot() && yaw == camera.yRot() &&
                sectionX==(camera.blockPosition().getX()>>4) && sectionY==(camera.blockPosition().getY()>>4) &&
                sectionZ==(camera.blockPosition().getZ()>>4) &&
                fov == camera.getFov() && mode == client.options.getCameraType() &&
                panoramic == camera.isPanoramicMode() && projection.equals(camera.getViewRotationProjectionMatrix(scratch));
    }
}
