package edu.vanderbilt.yunyulin.speechdrop.room;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class RoomData {
    public String name;
    public long ctime;

    @JsonCreator
    public RoomData(@JsonProperty("name") String name,
                    @JsonProperty("ctime") long ctime) {
        this.name = name;
        this.ctime = ctime;
    }
}
