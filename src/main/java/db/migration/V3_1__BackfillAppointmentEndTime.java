package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Time;
import java.time.LocalTime;

/**
 * Back-fills appointments.end_time (= start + service duration) for appointments created before V3, then makes the
 * column NOT NULL and adds its CHECK constraint. Written in Java because time arithmetic differs between PostgreSQL
 * and H2.
 */
public class V3_1__BackfillAppointmentEndTime extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        try (Statement select = connection.createStatement();
             ResultSet rs = select.executeQuery("""
                     SELECT a.id, a.appointment_time, s.estimated_duration_minutes
                     FROM appointments a JOIN services s ON s.id = a.service_id
                     WHERE a.end_time IS NULL
                     """);
             PreparedStatement update = connection.prepareStatement("UPDATE appointments SET end_time = ? WHERE id = ?")) {
            while (rs.next()) {
                LocalTime start = rs.getTime(2).toLocalTime();
                LocalTime end = start.plusMinutes(rs.getInt(3));
                if (!end.isAfter(start)) {
                    end = LocalTime.of(23, 59); // would wrap past midnight
                }
                update.setTime(1, Time.valueOf(end));
                update.setLong(2, rs.getLong(1));
                update.addBatch();
            }
            update.executeBatch();
        }
        try (Statement ddl = connection.createStatement()) {
            ddl.execute("ALTER TABLE appointments ALTER COLUMN end_time SET NOT NULL");
            ddl.execute("ALTER TABLE appointments ADD CONSTRAINT ck_appointments_end_time CHECK (end_time > appointment_time)");
        }
    }
}
