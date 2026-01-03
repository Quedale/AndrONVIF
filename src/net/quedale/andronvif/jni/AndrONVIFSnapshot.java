package net.quedale.andronvif.jni;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import java.io.Serializable;
import java.nio.ByteBuffer;

import dalvik.annotation.optimization.FastNative;

public class AndrONVIFSnapshot extends JNISoapObject implements Serializable {
    @FastNative //Not critical native because it returns a ByteBuffer
    private static native ByteBuffer getNativeBuffer(long pointer);

    private transient Bitmap bitmap;

    protected AndrONVIFSnapshot(long pointer) {
        super(pointer);
        ByteBuffer buffer = getNativeBuffer(this.pointer_address);
        byte[] imageBytes = new byte[buffer.remaining()];
        buffer.get(imageBytes);
        bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
    }

    public Bitmap getBitmap(){
        if(bitmap == null){
            Log.d("AndrONVIFSnapshot","Restoring bitmap");
            ByteBuffer buffer = getNativeBuffer(this.pointer_address);
            byte[] imageBytes = new byte[buffer.remaining()];
            buffer.get(imageBytes);
            bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
        }
        return bitmap;
    }
}
