package observable.server

import observable.Observable
import observable.Props
import kotlin.random.Random

/**
 * Tagged stack trace sampler.
 */
class TaggedSampler(val thread: Thread) : Runnable {
    override fun run() {
        Observable.LOGGER.info("Started sampler thread")
        val interval = ServerSettings.traceInterval.toLong()
        val deviation = ServerSettings.deviation.toLong()
        if (interval > 0) {
            while (!Props.notProcessing) {
                Props.currentTarget.get()?.let { target ->
                    target.traces.add(thread.stackTrace.toList())
                }
                Thread.sleep(interval + Random.nextLong(-deviation, deviation))
            }
        } else {
            while (!Props.notProcessing) {
                Props.currentTarget.get()?.let { target ->
                    target.traces.add(thread.stackTrace.toList())
                }
            }
        }
    }

}
