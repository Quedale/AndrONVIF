package net.quedale.andronvif.db;

import androidx.room.Database;
import androidx.room.RoomDatabase;

@Database(entities = {DBDevice.class}, version = 1)
public abstract class AndrONVIFDatabase extends RoomDatabase {
    public abstract DBDeviceDao deviceDao();
}
