package test

import example.MyProductionClass
import org.junit.Test

class MainIT {
    @Test
    fun mySimpleTest() {
        MyProductionClass().i = 10
    }
}
