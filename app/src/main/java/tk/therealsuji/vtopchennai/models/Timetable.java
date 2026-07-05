package tk.therealsuji.vtopchennai.models;

import static androidx.room.ForeignKey.CASCADE;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "timetable",
        foreignKeys = {
                @ForeignKey(
                        entity = Slot.class,
                        parentColumns = "id",
                        childColumns = "sunday",
                        onDelete = CASCADE
                ),
                @ForeignKey(
                        entity = Slot.class,
                        parentColumns = "id",
                        childColumns = "monday",
                        onDelete = CASCADE
                ),
                @ForeignKey(
                        entity = Slot.class,
                        parentColumns = "id",
                        childColumns = "tuesday",
                        onDelete = CASCADE
                ),
                @ForeignKey(
                        entity = Slot.class,
                        parentColumns = "id",
                        childColumns = "wednesday",
                        onDelete = CASCADE
                ),
                @ForeignKey(
                        entity = Slot.class,
                        parentColumns = "id",
                        childColumns = "thursday",
                        onDelete = CASCADE
                ),
                @ForeignKey(
                        entity = Slot.class,
                        parentColumns = "id",
                        childColumns = "friday",
                        onDelete = CASCADE
                ),
                @ForeignKey(
                        entity = Slot.class,
                        parentColumns = "id",
                        childColumns = "saturday",
                        onDelete = CASCADE
                )
        }
)
public class Timetable {
    @PrimaryKey(autoGenerate = true)
    public int id;

    @ColumnInfo(name = "start_time")
    public String startTime;

    @ColumnInfo(name = "end_time")
    public String endTime;

    @ColumnInfo(name = "sunday")
    public Integer sunday;

    @ColumnInfo(name = "monday")
    public Integer monday;

    @ColumnInfo(name = "tuesday")
    public Integer tuesday;

    @ColumnInfo(name = "wednesday")
    public Integer wednesday;

    @ColumnInfo(name = "thursday")
    public Integer thursday;

    @ColumnInfo(name = "friday")
    public Integer friday;

    @ColumnInfo(name = "saturday")
    public Integer saturday;

    public static class AllData {
        public int slotId;
        public String startTime;
        public String endTime;
        public String courseType;
        public String courseCode;
        public String courseTitle;
        public Integer attendancePercentage;
    }

    public static java.util.List<AllData> combineLabs(java.util.List<AllData> list) {
        if (list == null || list.size() < 2) {
            return list;
        }
        java.util.List<AllData> combined = new java.util.ArrayList<>();
        AllData current = null;
        for (AllData item : list) {
            if (current == null) {
                current = copyAllData(item);
                continue;
            }
            if ("lab".equalsIgnoreCase(current.courseType)
                    && "lab".equalsIgnoreCase(item.courseType)
                    && current.courseCode != null
                    && current.courseCode.equalsIgnoreCase(item.courseCode)) {
                current.endTime = item.endTime;
            } else {
                combined.add(current);
                current = copyAllData(item);
            }
        }
        if (current != null) {
            combined.add(current);
        }
        return combined;
    }

    private static AllData copyAllData(AllData original) {
        AllData copy = new AllData();
        copy.slotId = original.slotId;
        copy.startTime = original.startTime;
        copy.endTime = original.endTime;
        copy.courseType = original.courseType;
        copy.courseCode = original.courseCode;
        copy.courseTitle = original.courseTitle;
        copy.attendancePercentage = original.attendancePercentage;
        return copy;
    }
}
