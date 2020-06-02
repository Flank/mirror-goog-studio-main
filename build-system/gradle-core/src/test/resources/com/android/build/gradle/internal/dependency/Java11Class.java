import android.icu.math.BigDecimal;
import java.util.stream.IntStream;

public class Java11Class {
    public static void main(String args[]) {
        // 'var' keyword in lambda function
        IntStream.of(1, 2, 3, 5, 6, 7).filter((var i) -> i % 2 == 0).forEach(System.out::println);

        // android.icu.math.BigDecimal from System Module
        var num = new BigDecimal(123.456);
    }
}
