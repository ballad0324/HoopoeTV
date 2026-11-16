package com.github.tvbox.osc.cache;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import java.io.Serializable;

@Entity(tableName = "vodCollect",
        indices = {@Index(value = {"sourceKey", "vodId"}, unique = true)})
public class VodCollect implements Serializable {
    @PrimaryKey(autoGenerate = true)
    private int id;
    @ColumnInfo(name = "vodId")
    public String vodId;
    @ColumnInfo(name = "updateTime")
    public long updateTime;
    @ColumnInfo(name = "sourceKey")
    public String sourceKey;
    @ColumnInfo(name = "name")
    public String name;
    @ColumnInfo(name = "pic")
    public String pic;
    /** 收藏备注（手机端「已收藏 · 备注」/ 直播频道标记），电视端原先没有这一列 */
    @ColumnInfo(name = "note")
    public String note;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }
}