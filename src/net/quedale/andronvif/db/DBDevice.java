package net.quedale.andronvif.db;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity
public class DBDevice {
    public DBDevice(String address, int port, boolean tls, String username, String password){
        this.address = address;
        this.port = port;
        this.tls = tls;
        this.username = username;
        this.password = password;
    }

    @PrimaryKey
    public int uid;

    @ColumnInfo(name = "address")
    public String address;

    @ColumnInfo(name = "port")
    public int port;

    @ColumnInfo(name = "tls")
    public boolean tls;

    //TODO Use credential manager to save credentials?
    @ColumnInfo(name = "username")
    public String username;

    @ColumnInfo(name = "password")
    public String password;
}
