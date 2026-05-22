package com.guardianstar.parent.data;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.guardianstar.parent.data.dao.AppLimitDao;
import com.guardianstar.parent.data.dao.AppUsageDao;
import com.guardianstar.parent.data.dao.DeviceDao;
import com.guardianstar.parent.data.dao.SOSAlertDao;
import com.guardianstar.parent.data.dao.ScheduleDao;
import com.guardianstar.parent.data.dao.WebFilterDao;

@Database(entities = {
        Device.class,
        AppUsage.class,
        WebFilter.class,
        SOSAlert.class,
        AppLimit.class,
        Schedule.class
}, version = 2, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    private static AppDatabase instance;

    public abstract DeviceDao deviceDao();
    public abstract AppUsageDao appUsageDao();
    public abstract WebFilterDao webFilterDao();
    public abstract SOSAlertDao sosAlertDao();
    public abstract AppLimitDao appLimitDao();
    public abstract ScheduleDao scheduleDao();

    // 空迁移：版本1→2 无 schema 变更，保留所有数据
    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            // 无 schema 变更，仅升级版本号
        }
    };

    public static synchronized AppDatabase getInstance(Context context) {
        if (instance == null) {
            instance = Room.databaseBuilder(
                            context.getApplicationContext(),
                            AppDatabase.class,
                            "guardian_star_db"
                    )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration()
                    .build();
        }
        return instance;
    }
}
