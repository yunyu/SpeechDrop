package edu.vanderbilt.yunyulin.speechdrop.room;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class RoomData {
    public String name;
    public long ctime;
    public boolean disableDeletion;

    @JsonCreator
    public RoomData(@JsonProperty("name") String name,
                    @JsonProperty("ctime") long ctime,
                    @JsonProperty("disable_deletion") boolean disableDeletion) {
        this.name = name;
        this.ctime = ctime;
        this.disableDeletion = disableDeletion;
    }
}
