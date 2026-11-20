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
    private CameraType mode;
    private boolean panoramic;

    void capture(Minecraft client) {
        var camera = client.gameRenderer.mainCamera();
        position = camera.position();
        pitch = camera.xRot();
        yaw = camera.yRot();
        fov = camera.getFov();
        mode = client.options.getCameraType();
        panoramic = camera.isPanoramicMode();
        camera.getViewRotationProjectionMatrix(projection);
    }

    boolean matches(Minecraft client) {
        var camera = client.gameRenderer.mainCamera();
        return camera.position().equals(position) && pitch == camera.xRot() && yaw == camera.yRot() &&
                fov == camera.getFov() && mode == client.options.getCameraType() &&
                panoramic == camera.isPanoramicMode() && projection.equals(camera.getViewRotationProjectionMatrix(scratch));
    }
}
