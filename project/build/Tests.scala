



import collection._





object Tests {
  trait TestInfo {
    def classname: String
    def arguments: Seq[Seq[String]]
    def logfile = "testlog.txt"
    
    def defaults = Seq(Seq("logfile=" + logfile))
    def foreach[U](f: String => U) = rec_foreach(Seq(), arguments, f)
    private def rec_foreach[U](argsaccum: Seq[String], left: Seq[Seq[String]], f: String => U) {
      if (left.isEmpty) f(argsaccum.foldLeft("")(_ + " " + _))
      else for (argseq <- left(0)) rec_foreach(argsaccum ++ List(argseq), left.drop(1), f)
    }
  }
  
  object ParallelTests extends TestInfo {
    def classname = "scala.threads.ParallelTests"
    def arguments = Seq(
      Seq(
        // "loop_local_write",
        // "loop_heap_read", 
        // "loop_heap_write",
        // "loop_vread", 
        // "loop_vwrite",
        // "loop_atomic_read", 
        // "loop_atomic_write",
        "loop_atomic_cas",
        "loop_fieldupdater_cas",
        // "loop_atomic_tlocal_cas",
        // "loop_atomic_weakcas",
        // "loop_atomic_tlocal_weakcas",
        // "conchashmap_insert",
        // "concskiplist_insert",
        // "linear_insert",
        // "currthread",
        // "threadlocal"
      ) map { "testname=" + _ },
      Seq(1, 2, 4, 8) map { "threadnum=" + _ },
      Seq("totalwork=2000000"),
      Seq("numtests=200"),
      Seq("logging=true")
    ) ++ defaults
  }
  
  object CTries extends TestInfo {
    def classname = "scala.threads.CTries"
    def arguments = defaults
  }
  
  object Sched extends TestInfo {
    def classname = "scala.threads.Sched"
    def arguments = Seq(
      Seq(
        // "loop",
        "takepiece",
      ) map { "testname=" + _ },
      Seq("25000", "50000", "100000", "200000", "400000") map {"totalwork=" + _},
      Seq(1, 2, 4) map { "threadnum=" + _ },
      Seq("numtests=400"),
      Seq("logging=false")
    ) ++ defaults
  }
  
  val map: Map[String, TestInfo] = mutable.Map() ++ (List(
    ParallelTests,
    CTries,
    Sched
  ) map { t => (t.classname, t) })
  
  def apply(nm: String) = map(nm)
}
