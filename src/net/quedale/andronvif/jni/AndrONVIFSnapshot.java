package net.quedale.andronvif.jni;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.nio.ByteBuffer;

import dalvik.annotation.optimization.FastNative;

public class AndrONVIFSnapshot extends JNISoapObject {
    @FastNative //Not critical native because it returns a ByteBuffer
    private static native ByteBuffer getNativeBuffer(long pointer);

    private final Bitmap bitmap;

    protected AndrONVIFSnapshot(long pointer) {
        super(pointer);
        ByteBuffer buffer = getNativeBuffer(this.pointer_address);
        byte[] imageBytes = new byte[buffer.remaining()];
        buffer.get(imageBytes);
        bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
    }

    public Bitmap getBitmap(){
        return bitmap;
    }
}
