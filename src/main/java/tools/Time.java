package tools;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class Time {

    public Time(){
    }

    public static long currentTimeInMillis() {
        return System.currentTimeMillis();
    }

    /*
     * Returns The date in the Format dd-MM-yyy HH:mm:ss
     */
    public static String getDate_DD_MM_YY_HH_MM_SS(){
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd-MM-yyy HH:mm:ss");
        return dtf.format(LocalDateTime.now());
    }
}
