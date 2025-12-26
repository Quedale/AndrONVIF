package net.quedale.andronvif.db;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface DBDeviceDao {
    @Query("SELECT * FROM dbdevice")
    List<DBDevice> getAll();

    @Insert
    void insertAll(DBDevice... devices);

    @Delete
    void delete(DBDevice user);
}
