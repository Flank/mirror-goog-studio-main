package pkg

import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.*

class KotlinCoroutineTarget {
  fun getStatus() : String {
    val c = AtomicLong()

    for (i in 1..100L) {
        GlobalScope.launch {
            c.addAndGet(i-i);
        }
    }

    return "KotlinCoroutineTarget " + c.get();
  }
}
