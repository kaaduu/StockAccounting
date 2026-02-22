package cz.datesoft.stockAccounting;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Date;

class TransactionTest {

    @Test
    void basicArithmeticWorks() {
        int result = 2 + 2;
        assertEquals(4, result, "2 + 2 should equal 4");
    }

    @Test
    void currentDateIsNotNull() {
        Date date = new Date();
        assertNotNull(date, "Current date should not be null");
    }
}
