package scala.threads

import java.util.concurrent.atomic._

// S sets test set size
// Thrds sets number of threads to use
// Blk set number of units of work taken from thread work. Smaller gives better granulation but more OH
// This avoids getting stuck on the last few if one is very unbalanced.

/*
Notes by Phil
A collection is assumed to have n items and must do work on each item in the range 0 to n
to produce some result
The principle used here is to split the range among p threads, one for each processor
If a thread completes the assigned range then it trys to steal the uncompleted range
from another thread.
As there is overhead in the stealing process the threads secure work in blocks from their given range. 
These cannot be stolen. The stealing cost is negligible when amortised over the block size.
If another thread cannot find work then it can request that a thread working on its last block
should share some of that work. This is less efficient than a steal. On average the requesting
thread must wait half the time for one index worth of work. In general this is small. 
On completing its current work for one index step, it returns any uncompleted range 
to its range pool so it is available to be stolen. The waiting thread can now acquire some of it.
(different strategies may give slight improvements, returning half for example) 
This allows load balancing to a granularity of 1 in the index range.   
*/

object app extends Application {
  var tsterr = 0
  for (t <- 0 until 16) { if (t % 2 == 0) println("test,errs", t, tsterr); if (ttest) tsterr += 1 }
  if (tsterr > 0) println("***Errors***", tsterr)
  def ttest = {
    val S = 100000000
    val Thrds = 4
    val ThrdSize = S / Thrds
    val Threads = new Array[MyThread](Thrds)
    val a = new Array[Int](S / 10) // TODO
    for (i <- 0 until Thrds) {
      val strt = i * ThrdSize
      val end = if (i == Thrds - 1) S else (i + 1) * ThrdSize

      val nt = new MyThread() {
        override def run() {
          val thrstart = System.nanoTime
          tn = i
          totalWork = S
          doWork(strt, end)
          val thrend = System.nanoTime
          totalTime = (thrend - thrstart)
        }
      }
      Threads(i) = nt
      nt.threads = Threads
      nt.wa = a
    }
    // Timings 
    val ixst = System.nanoTime()
    Threads.foreach(t => t.start())
    Threads.foreach(t => t.join())
    val xtm = System.nanoTime() - ixst
    for (t <- Threads) println("Thread " + t.tn + " time: " + (t.totalTime) / 1e3)
    println("time", xtm.toFloat / 1e3)

    // Report Statistics
    // Check for and report errors detected in array values
    var err = false
    for (i <- 0 until S) {
      //if(a(i)!=i){err=true;println(i,a(i))}
    }

    //if(err){ println("Error")  report on error
    if (true) { // report always
      println("Retries - total spin count waiting for a share (thread,retries)") // Indicates how much time is wasted in a thread looking for work
      Threads.foreach(t => if (t.retries > 0) println(t.tn, t.retries))
      println("Steals - work stolen from another thread (thread, start, end)") // reports the steals into a thread
      Threads.foreach(t => println(t.steals))
      println("Shares - work given back for sharing (thread, given back, retained) ") // reports the shares given by a thread
      Threads.foreach(t => println(t.shares))
    }
    err
  }
}

class NormalInteger {
  private var i = 0
  def get = i
  def set(n: Int) = i = n
  def compareAndSet(oldval: Int, newval: Int) = { i = newval; true }
}

class MyThread extends Thread {
  val Blk = 100 // defines size of work blocks (amortises the atomics in getwork but increases granularity) 
  var wa: Array[Int] = null
  var threads: Array[MyThread] = null
  val work = new AtomicInteger(0)
  val isLocked = new AtomicBoolean(false)
  var max = new AtomicInteger(0)
  @volatile var isWorking = true
  @volatile var shareWork = false
  var tx = 0 // Work index
  var tn = 0 // Thread number
  // for debugging 
  var retries = 0 // Retry count
  var steals = List[(Int, Int, Int)]() // Records steals
  var shares = List[(Int, Int, Int)]() // Records shares
  var totalTime = 0L
  var totalWork = 0

  def getWork = {
    var w = 0
    var dw = 0
    var nw = 0
    do {
      w = work.get
      dw = if (w >= Blk) Blk
           else if (w > 0) w / 2 + 1 else 0
      nw = w - dw
    } while (!work.compareAndSet(w, nw))
    dw
  }

  // Artificial work load 
  def workLoad() = {
    // if(wa(tx)!=0)println("rng err")
    //wa(tx)=tx	 set values in an array.
    tx += 1
    // dummy work load

    // Change the work as a function of the Index (tx) to simulate imbalanced work.
    //val wkPerIndex =if(tx<100)1000000 else heavily skew to first 400
    //val wkPerIndex =if(tx>totalWork-100)1000000 else 10 heavily skew to last 400 
    val wkPerIndex = 10 // small uniform
    var c = 0
    var i = 0
    if (true) {
      while (i < wkPerIndex * 3) { c += 1; i += 1 } // This works fine and gives expected results 
    } else {
      // Curiously, the following alternative causes memory contention with multiple threads
      // that results in a significant slow down so that 2 threads are almost the same speed as one! 
      for (i <- 0 until wkPerIndex) c += 1
    }
    c // force compiler not to optimise out un-used results and code
  }

  // Basic work loop 
  def doWork(start: Int, end: Int) = {
    //println(tn,"start",start,end,load)
    max.set(end)
    tx = start
    work.set(end - start)
    var w = getWork
    do {
      while (w > 0) {
        isWorking = true
        var k = 0
        while (k < w) {
          // Check to see if work sharing requested
          
          // if(shareWork){
          //   if(work.get==0) {
          //     val dw = w-k
          //     work.set(dw)
          //     w -= dw
          //     shares = (tn,dw,w-k) :: shares  // for debugging only
          //   }
          //   shareWork = false // TODO
          // }
          
          workLoad() // The work, a dummy in this case
          k += 1
        }
        w = getWork
      }
      isWorking = false
      shareWork = false

      // Finished work so go look for more
      val (strt, end) = findWork
      // Set up new work
      tx = strt // set index (could also be the point to setup a new iterator)
      max.set(end) // set max index value       
      work.set(end - strt) // set how many units of work to do
      w = getWork // get first block of work
    } while (w > 0) // Exit thread when no more work to do
    //println(tn,"finish")
  }

  // Steals work from this thread	
  def stealWork = {
    var w = 0
    var dw = 0
    var nw = 0
    var nmax = 0
    do {
      w = work.get
      dw = if (w > 0) w / 2 + 1 else 0
      nw = w - dw
      nmax = max.get // must save current max before committing the steal
    } while (!work.compareAndSet(w, nw))
    // If thread stalls here then max can be updated in the dowork
    // in dowork the thread finishes its work and then steals from another thread
    // After doing this it updates max. That value will now be overwritten 
    // here when the thread continues and erases the new value.
    // since it has been stolen from another thread it cannot be the same max as for this one
    // so check if changed, if so then do not update
    max.compareAndSet(nmax, nmax - dw)
    //for(i<-0 until 1000)dw=dw
    //if(dw>0)println(tn,"Steal",nmax-dw,nmax,isLocked.get) report stealing
    (nmax - dw, nmax)
  }

  // Finds work in other threads.
  // It tests a thread and if no work or already being stolen from tries another
  // Current strategy is sequential but random steps easily done too.
  def findWork = {
    var st = 0
    var ed = 0
    var retry = false
    val Thrds = threads.length
    do {
      retry = false
      // looking for work starts from self+1 and then cyles through all other threads.
      // This could be randomised but no real advantage, clustering cannot occur in this model.
      var s = if (tn + 1 == Thrds) 0 else tn + 1
      do {
        val tt = threads(s)
        if (!tt.isLocked.getAndSet(true)) {
          val (strt, end) = tt.stealWork
          tt.isLocked.set(false)
          st = strt
          ed = end
          if (st != ed) retry = false
          else if (tt.isWorking) { retry = true; tt.shareWork = true }
          if (strt != end) steals = (tn, strt, end) :: steals // for debugging only
        } else {
          //println("locked") report when blocked for debugging
          st = ed
          if (tt.isWorking) retry = true
        }
        s = if (s + 1 == Thrds) 0 else s + 1
      } while ((st == ed) && (s != tn))
      if (retry) retries += 1 // for debugging
    } while (retry)
    (st, ed)
  }
}

