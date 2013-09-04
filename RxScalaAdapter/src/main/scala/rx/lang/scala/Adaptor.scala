
package rx.lang.scala

import scala.collection.JavaConverters._
import rx.{Observable => JObservable}
import rx.util.functions.Action1
import rx.Subscription
import rx.lang.scala.ImplicitFunctionConversions._
import rx.util.BufferClosing
import rx.util.BufferOpening
import rx.observables.GroupedObservable
import rx.Scheduler
import rx.Observer
import rx.util.functions.Func2

/*
 * TODO:
 * - once/if RxJava has covariant Observable, make ScalaObservable covariant, too
 * - many TODOs below
 */
// We have this `object Adaptor` because we cannot define the implicit class ScalaObservable at top level
object Adaptor {

  object Observable {
    
    def apply[T](args: T*): JObservable[T] = {     
      JObservable.from(args.toIterable.asJava)
    }
    
    def apply[T](iterable: Iterable[T]): JObservable[T] = {
      JObservable.from(iterable.asJava)
    }
    
    def apply() = {
      JObservable.empty()
    }
    
    // TODO: apply() to construct from Scala futures, converting them first to Java futures
    
    def apply[T](func: Observer[T] => Subscription): JObservable[T] = {
      JObservable.create(func);
    }
    
    def apply(exception: Throwable): JObservable[_] = {
      JObservable.error(exception)
    }
    
    // call by name
    def defer[T](observable: => JObservable[T]): JObservable[T] = {
      JObservable.defer(observable)
    }
    
    // These are the static methods of JObservable that we don't need here:
    
    // concat         because it's implemented as instance method ++:
    // zip            because it's an instance method
    
    // These are the static methods of JObservable implemented as overloads of apply():
    
    // 	<T> Observable<T> create(Func1<Observer<T>,Subscription> func)
    // 	<T> Observable<T> empty()
    // 	<T> Observable<T> error(java.lang.Throwable exception)    
    // 	<T> Observable<T> from(java.util.concurrent.Future<T> future)
    // 	<T> Observable<T> from(java.util.concurrent.Future<T> future, long timeout, java.util.concurrent.TimeUnit unit)
    // 	<T> Observable<T> from(java.util.concurrent.Future<T> future, Scheduler scheduler)
    // 	<T> Observable<T> from(java.lang.Iterable<T> iterable)
    // 	<T> Observable<T> from(T... items)
    // 	<T> Observable<T> just(T value)
    
    // TODO decide on these static methods how to bring them to Scala:
    
    // 	<R,T0,T1> Observable<R>	      combineLatest(Observable<T0> w0, Observable<T1> w1, Func2<T0,T1,R> combineFunction)
    // 	<R,T0,T1,T2> Observable<R>	  combineLatest(Observable<T0> w0, Observable<T1> w1, Observable<T2> w2, Func3<T0,T1,T2,R> combineFunction)
    // 	<R,T0,T1,T2,T3> Observable<R> combineLatest(Observable<T0> w0, Observable<T1> w1, Observable<T2> w2, Observable<T3> w3, Func4<T0,T1,T2,T3,R> combineFunction)
    // 	<T> Observable<T>             merge(java.util.List<Observable<T>> source)
    // 	<T> Observable<T>             merge(Observable<Observable<T>> source)
    // 	<T> Observable<T>             merge(Observable<T>... source)
    // 	<T> Observable<T>             mergeDelayError(java.util.List<Observable<T>> source)
    // 	<T> Observable<T>             mergeDelayError(Observable<Observable<T>> source)
    // 	<T> Observable<T>             mergeDelayError(Observable<T>... source)
    // 	<T> Observable<T>             never()
    //  Observable<java.lang.Integer> range(int start, int count)
    // 	<T> Observable<Boolean>       sequenceEqual(Observable<T> first, Observable<T> second)
    // 	<T> Observable<Boolean>       sequenceEqual(Observable<T> first, Observable<T> second, Func2<T,T,java.lang.Boolean> equality)
    // 	<T> Observable<T>             switchDo(Observable<Observable<T>> sequenceOfSequences)
    // 	<T> Observable<T>             synchronize(Observable<T> observable)

  }
  
  // zero runtime overhead because it's a value class :)
  // return types are always types from the Java world, i.e. types such as
  // JObservable[T], JObservable[java.util.List[T]]
  implicit class ScalaObservable[T](val wrapped: JObservable[T]) extends AnyVal {

    ////////////// ScalaObservable Section 1 ///////////////////////////////////
    
    /* Section 1 contains methods that have different signatures in Scala than
     * in RxJava */
    
    // TODO aggregate works differently in Scala
    
    // use Scala naming
    def forall(predicate: T => scala.Boolean): JObservable[java.lang.Boolean] = {
      wrapped.all(predicate)
    }
    
    def drop(n: Int): JObservable[T] = {
      wrapped.skip(n)
    }
    
    // no mapMany, because that's flatMap   
    
    // no reduce[R](initialValue: R, accumulator: (R, T) => R): JObservable[R] 
    // because that's called fold in Scala, and it's curried
    def fold[R](initialValue: R)(accumulator: (R, T) => R): JObservable[R] = {
      wrapped.fold(initialValue)(accumulator)
    }
    
    // no scan(accumulator: (T, T) => T ): JObservable[T] 
    // because Scala does not have scan without initial value

    // scan with initial value is curried in Scala
    def scan[R](initialValue: R)(accumulator: (R, T) => R): JObservable[R] = {
      wrapped.scan(initialValue, accumulator)
    }
    
    // TODO is this what we want?
    def foreach(f: T => Unit): Unit = wrapped.toBlockingObservable.forEach(f)
    
    def withFilter(p: T => Boolean): WithFilter[T] = new WithFilter[T](p, wrapped)
      
    // TODO: make zipWithIndex method
    
    // TODO: if we have zipWithIndex, takeWhileWithIndex is not needed any more
    def takeWhileWithIndex(predicate: (T, Integer) => Boolean): JObservable[T] = {
      wrapped.takeWhileWithIndex(predicate)
    }
    
    // where is not needed because it's called filter in Scala
    
    // we want zip as an instance method
    def zip[U](that: JObservable[U]): JObservable[(T, U)] = {
      JObservable.zip(wrapped, that, (t: T, u: U) => (t, u))
    }
    
    
    ////////////// ScalaObservable Section 2 ///////////////////////////////////
    
    /* Section 2 is just boilerplate code: it contains all instance methods of 
     * JObservable which take Func or Action as argument and don't need any
     * other change in their signature */  
    
    // It would be nice to have Section 2 in a trait ScalaObservableBoilerplate[T],
    // but traits cannot (yet) be mixed into value classes (unless someone shows me how ;-) )
    
    // Once the bug that parameter type inference of implicitly converted functions does not work 
    // in Scala 2.10 is fixed or Scala 2.10 is obsolete, we can see whether we can throw away Section 2
    // by requiring that RxScala users import ImplicitFunctionConversions._
    // Bug link: https://issues.scala-lang.org/browse/SI-6221

    def buffer(bufferClosingSelector: () => JObservable[BufferClosing]): JObservable[java.util.List[T]] = {
      wrapped.buffer(bufferClosingSelector)
    }

    def buffer(bufferOpenings: JObservable[BufferOpening], bufferClosingSelector: BufferOpening => JObservable[BufferClosing]): JObservable[java.util.List[T]] = {
      wrapped.buffer(bufferOpenings, bufferClosingSelector)
    }

    def filter(predicate: T => Boolean): JObservable[T] = {
      wrapped.filter(predicate)
    }

    def finallyDo(action: () => Unit): JObservable[T] = {
      wrapped.finallyDo(action)
    }

    def flatMap[R](func: T => JObservable[R]): JObservable[R] = {
      wrapped.flatMap(func)
    }

    def groupBy[K](keySelector: T => K ): JObservable[GroupedObservable[K,T]] = {
      wrapped.groupBy(keySelector)
    }

    def groupBy[K,R](keySelector: T => K, elementSelector: T => R ): JObservable[GroupedObservable[K,R]] = {
      wrapped.groupBy(keySelector, elementSelector)
    }

    def map[R](func: T => R): JObservable[R] = {
      wrapped.map(func)
    }

    def onErrorResumeNext(resumeFunction: Throwable => JObservable[T]): JObservable[T] = {
      wrapped.onErrorResumeNext(resumeFunction)
    }

    def onErrorReturn(resumeFunction: Throwable => T): JObservable[T] = {
      wrapped.onErrorReturn(resumeFunction)
    }

    def reduce(accumulator: (T, T) => T): JObservable[T] = {
      wrapped.reduce(accumulator)
    }

    def subscribe(onNext: T => Unit): Subscription = {
      wrapped.subscribe(onNext)
    }

    def subscribe(onNext: T => Unit, onError: Throwable => Unit): Subscription = {
      wrapped.subscribe(onNext, onError)
    }

    def subscribe(onNext: T => Unit, onError: Throwable => Unit, onComplete: () => Unit): Subscription = {
      wrapped.subscribe(onNext, onError, onComplete)
    }

    def subscribe(onNext: T => Unit, onError: Throwable => Unit, onComplete: () => Unit, scheduler: Scheduler): Subscription = {
      wrapped.subscribe(onNext, onError, onComplete, scheduler)
    }

    def subscribe(onNext: T => Unit, onError: Throwable => Unit, scheduler: Scheduler): Subscription = {
      wrapped.subscribe(onNext, onError, scheduler)
    }

    def subscribe(onNext: T => Unit, scheduler: Scheduler): Subscription = {
      wrapped.subscribe(onNext, scheduler)
    }

    def takeWhile(predicate: T => Boolean): JObservable[T] = {
      wrapped.takeWhile(predicate)
    }

    def toSortedList(sortFunction: (T, T) => Integer): JObservable[java.util.List[T]] = {
      wrapped.toSortedList(sortFunction)
    }
    
    ////////////// ScalaObservable Section 3 ///////////////////////////////////
    
    /* Section 3 contains all instance methods of JObservable which do not take
     * Func or Action as arguments. Since they can be used directly in Scala,
     * this section is empty. */
    
  }
  
  // Cannot yet have inner class because of this error message:
  // implementation restriction: nested class is not allowed in value class.
  // This restriction is planned to be removed in subsequent releases.  
  class WithFilter[T] private[Adaptor] (p: T => Boolean, wrapped: rx.Observable[T]) {
    def map[B](f: T => B): JObservable[B] = wrapped.filter(p).map(f)
    def flatMap[B](f: T => JObservable[B]): JObservable[B] = wrapped.filter(p).flatMap(f)
    def foreach(f: T => Unit): Unit = wrapped.filter(p).toBlockingObservable.forEach(f)
    def withFilter(p: T => Boolean): JObservable[T] = wrapped.filter(p)
  }
  
     
}

import org.scalatest.junit.JUnitSuite

class UnitTestSuite extends JUnitSuite {
    import rx.lang.scala.Adaptor._

    import org.junit.{ Before, Test }
    import org.junit.Assert._
    import org.mockito.Matchers.any
    import org.mockito.Mockito._
    import org.mockito.{ MockitoAnnotations, Mock }
    import rx.{ Notification, Observer, Observable, Subscription }
    import rx.observables.GroupedObservable
    import collection.mutable.ArrayBuffer
    import collection.JavaConverters._
            
    @Mock private[this]
    val observer: Observer[Any] = null
    
    @Mock private[this]
    val subscription: Subscription = null
    
    val isOdd = (i: Int) => i % 2 == 1
    val isEven = (i: Int) => i % 2 == 0
    
    class ObservableWithException(s: Subscription, values: String*) extends Observable[String] {
        var t: Thread = null
        
        override def subscribe(observer: Observer[String]): Subscription = {
            println("ObservableWithException subscribed to ...")
            t = new Thread(new Runnable() {
                override def run() {
                    try {
                        println("running ObservableWithException thread")
                        values.toList.foreach(v => {
                            println("ObservableWithException onNext: " + v)
                            observer.onNext(v)
                        })
                        throw new RuntimeException("Forced Failure")
                    } catch {
                        case ex: Exception => observer.onError(ex)
                    }
                }
            })
            println("starting ObservableWithException thread")
            t.start()
            println("done starting ObservableWithException thread")
            s
        }
    }
    
    @Before def before {
        MockitoAnnotations.initMocks(this)
    }
    
    // tests of static methods
    
    @Test def testSingle {
        assertEquals(1, Observable.from(1).toBlockingObservable.single)
    }
    
    @Test def testSinglePredicate {
        val found = Observable.from(1, 2, 3).toBlockingObservable.single(isEven)
        assertEquals(2, found)
    }
    
    @Test def testSingleOrDefault {
        assertEquals(0, Observable.from[Int]().toBlockingObservable.singleOrDefault(0))
        assertEquals(1, Observable.from(1).toBlockingObservable.singleOrDefault(0))
        try {
            Observable.from(1, 2, 3).toBlockingObservable.singleOrDefault(0)
            fail("Did not catch any exception, expected IllegalStateException")
        } catch {
            case ex: IllegalStateException => println("Caught expected IllegalStateException")
            case ex: Throwable => fail("Caught unexpected exception " + ex.getCause + ", expected IllegalStateException")
        }
    }
    
    @Test def testSingleOrDefaultPredicate {
        assertEquals(2, Observable.from(1, 2, 3).toBlockingObservable.singleOrDefault(0, isEven))
        assertEquals(0, Observable.from(1, 3).toBlockingObservable.singleOrDefault(0, isEven))
        try {
            Observable.from(1, 2, 3).toBlockingObservable.singleOrDefault(0, isOdd)
            fail("Did not catch any exception, expected IllegalStateException")
        } catch {
            case ex: IllegalStateException => println("Caught expected IllegalStateException")
            case ex: Throwable => fail("Caught unexpected exception " + ex.getCause + ", expected IllegalStateException")
        }
    }
    
    @Test def testFromJavaInterop {
        val observable = Observable.from(List(1, 2, 3).asJava)
        assertSubscribeReceives(observable)(1, 2, 3)
    }
    
    @Test def testSubscribe {
        val observable = Observable.from("1", "2", "3")
        assertSubscribeReceives(observable)("1", "2", "3")
    }
    
    //should not compile - adapted from https://gist.github.com/jmhofer/5195589
    /*@Test def testSubscribeOnInt() {
        val observable = Observable.from("1", "2", "3")
        observable.subscribe((arg: Int) => {
            println("testSubscribe: arg = " + arg)
        })
     }*/
    
    @Test def testDefer {
        val lazyObservableFactory = () => Observable.from(1, 2)
        val observable = Observable.defer(lazyObservableFactory)
        assertSubscribeReceives(observable)(1, 2)
    }
    
    @Test def testJust {
        val observable = Observable.just("foo")
        assertSubscribeReceives(observable)("foo")
    }
    
    @Test def testMerge {
        val observable1 = Observable.from(1, 2, 3)
        val observable2 = Observable.from(4, 5, 6)
        val observableList = List(observable1, observable2).asJava
        val merged = Observable.merge(observableList)
        assertSubscribeReceives(merged)(1, 2, 3, 4, 5, 6)
    }
    
    @Test def testFlattenMerge {
        val observable = Observable.from(Observable.from(1, 2, 3))
        val merged = Observable.merge(observable)
        assertSubscribeReceives(merged)(1, 2, 3)
    }
    
    @Test def testSequenceMerge {
        val observable1 = Observable.from(1, 2, 3)
        val observable2 = Observable.from(4, 5, 6)
        val merged = Observable.merge(observable1, observable2)
        assertSubscribeReceives(merged)(1, 2, 3, 4, 5, 6)
    }
    
    @Test def testConcat {
        val observable1 = Observable.from(1, 2, 3)
        val observable2 = Observable.from(4, 5, 6)
        val concatenated = Observable.concat(observable1, observable2)
        assertSubscribeReceives(concatenated)(1, 2, 3, 4, 5, 6)
    }
    
    @Test def testSynchronize {
        val observable = Observable.from(1, 2, 3)
        val synchronized = Observable.synchronize(observable)
        assertSubscribeReceives(synchronized)(1, 2, 3)
    }
    
    @Test def testZip3() {
        val numbers = Observable.from(1, 2, 3)
        val colors = Observable.from("red", "green", "blue")
        val names = Observable.from("lion-o", "cheetara", "panthro")
        
        case class Character(id: Int, color: String, name: String)
        
        val liono = Character(1, "red", "lion-o")
        val cheetara = Character(2, "green", "cheetara")
        val panthro = Character(3, "blue", "panthro")
        
        val characters = Observable.zip(numbers, colors, names, Character.apply _)
        assertSubscribeReceives(characters)(liono, cheetara, panthro)
    }
    
    @Test def testZip4() {
        val numbers = Observable.from(1, 2, 3)
        val colors = Observable.from("red", "green", "blue")
        val names = Observable.from("lion-o", "cheetara", "panthro")
        val isLeader = Observable.from(true, false, false)
        
        case class Character(id: Int, color: String, name: String, isLeader: Boolean)
        
        val liono = Character(1, "red", "lion-o", true)
        val cheetara = Character(2, "green", "cheetara", false)
        val panthro = Character(3, "blue", "panthro", false)
        
        val characters = Observable.zip(numbers, colors, names, isLeader, Character.apply _)
        assertSubscribeReceives(characters)(liono, cheetara, panthro)
    }
    
    //tests of instance methods
    
    // missing tests for : takeUntil, groupBy, next, mostRecent
    
    @Test def testFilter {
        val numbers = Observable.from(1, 2, 3, 4, 5, 6, 7, 8, 9)
        val observable = numbers.filter(isEven)
        assertSubscribeReceives(observable)(2, 4, 6, 8)
    }
    
    @Test def testLast {
        val observable = Observable.from(1, 2, 3, 4).toBlockingObservable
        assertEquals(4, observable.toBlockingObservable.last)
    }
    
    @Test def testLastPredicate {
        val observable = Observable.from(1, 2, 3, 4)
        assertEquals(3, observable.toBlockingObservable.last(isOdd))
    }
    
    @Test def testLastOrDefault {
        val observable = Observable.from(1, 2, 3, 4)
        assertEquals(4, observable.toBlockingObservable.lastOrDefault(5))
        assertEquals(5, Observable.from[Int]().toBlockingObservable.lastOrDefault(5))
    }
    
    @Test def testLastOrDefaultPredicate {
        val observable = Observable.from(1, 2, 3, 4)
        assertEquals(3, observable.toBlockingObservable.lastOrDefault(5, isOdd))
        assertEquals(5, Observable.from[Int]().toBlockingObservable.lastOrDefault(5, isOdd))
    }
    
    @Test def testMap {
        val numbers = Observable.from(1, 2, 3, 4, 5, 6, 7, 8, 9)
        val mappedNumbers = ArrayBuffer.empty[Int]
        numbers.map((x: Int) => x * x).subscribe((squareVal: Int) => {
            mappedNumbers.append(squareVal)
        })
        assertEquals(List(1, 4, 9, 16, 25, 36, 49, 64, 81), mappedNumbers.toList)
    }
    
    @Test def testMapMany {
        val numbers = Observable.from(1, 2, 3, 4)
        val f = (i: Int) => Observable.from(List(i, -i).asJava)
        val mappedNumbers = ArrayBuffer.empty[Int]
        numbers.mapMany(f).subscribe((i: Int) => {
            mappedNumbers.append(i)
        })
        assertEquals(List(1, -1, 2, -2, 3, -3, 4, -4), mappedNumbers.toList)
    }
    
    @Test def testMaterialize {
        val observable = Observable.from(1, 2, 3, 4)
        val expectedNotifications: List[Notification[Int]] =
            ((1.to(4).map(i => new Notification(i))) :+ new Notification()).toList
        val actualNotifications: ArrayBuffer[Notification[Int]] = ArrayBuffer.empty
        observable.materialize.subscribe((n: Notification[Int]) => {
            actualNotifications.append(n)
        })
        assertEquals(expectedNotifications, actualNotifications.toList)
    }
    
    @Test def testDematerialize {
        val notifications: List[Notification[Int]] =
            ((1.to(4).map(i => new Notification(i))) :+ new Notification()).toList
        val observableNotifications: Observable[Notification[Int]] =
            Observable.from(notifications.asJava)
        val observable: Observable[Int] =
            observableNotifications.dematerialize()
        assertSubscribeReceives(observable)(1, 2, 3, 4)
    }
    
    @Test def testOnErrorResumeNextObservableNoError {
        val observable = Observable.from(1, 2, 3, 4)
        val resumeObservable = Observable.from(5, 6, 7, 8)
        val observableWithErrorHandler = observable.onErrorResumeNext(resumeObservable)
        assertSubscribeReceives(observableWithErrorHandler)(1, 2, 3, 4)
    }
    
    @Test def testOnErrorResumeNextObservableErrorOccurs {
        val observable = new ObservableWithException(subscription, "foo", "bar")
        val resumeObservable = Observable.from("a", "b", "c", "d")
        val observableWithErrorHandler = observable.onErrorResumeNext(resumeObservable)
        observableWithErrorHandler.subscribe(observer.asInstanceOf[Observer[String]])
        
        try {
            observable.t.join()
        } catch {
            case ex: InterruptedException => fail(ex.getMessage)
        }
        
        List("foo", "bar", "a", "b", "c", "d").foreach(t => verify(observer, times(1)).onNext(t))
        verify(observer, never()).onError(any(classOf[Exception]))
        verify(observer, times(1)).onCompleted()
    }
    
    @Test def testOnErrorResumeNextFuncNoError {
        val observable = Observable.from(1, 2, 3, 4)
        val resumeFunc = (ex: Throwable) => Observable.from(5, 6, 7, 8)
        val observableWithErrorHandler = observable.onErrorResumeNext(resumeFunc)
        assertSubscribeReceives(observableWithErrorHandler)(1, 2, 3, 4)
    }
    
    @Test def testOnErrorResumeNextFuncErrorOccurs {
        val observable = new ObservableWithException(subscription, "foo", "bar")
        val resumeFunc = (ex: Throwable) => Observable.from("a", "b", "c", "d")
        val observableWithErrorHandler = observable.onErrorResumeNext(resumeFunc)
        observableWithErrorHandler.subscribe(observer.asInstanceOf[Observer[String]])
        
        try {
            observable.t.join()
        } catch {
            case ex: InterruptedException => fail(ex.getMessage)
        }
        
        List("foo", "bar", "a", "b", "c", "d").foreach(t => verify(observer, times(1)).onNext(t))
        verify(observer, never()).onError(any(classOf[Exception]))
        verify(observer, times(1)).onCompleted()
    }
    
    @Test def testOnErrorReturnFuncNoError {
        val observable = Observable.from(1, 2, 3, 4)
        val returnFunc = (ex: Throwable) => 87
        val observableWithErrorHandler = observable.onErrorReturn(returnFunc)
        assertSubscribeReceives(observableWithErrorHandler)(1, 2, 3, 4)
    }
    
    @Test def testOnErrorReturnFuncErrorOccurs {
        val observable = new ObservableWithException(subscription, "foo", "bar")
        val returnFunc = (ex: Throwable) => "baz"
        val observableWithErrorHandler = observable.onErrorReturn(returnFunc)
        observableWithErrorHandler.subscribe(observer.asInstanceOf[Observer[String]])
        
        try {
            observable.t.join()
        } catch {
            case ex: InterruptedException => fail(ex.getMessage)
        }
        
        List("foo", "bar", "baz").foreach(t => verify(observer, times(1)).onNext(t))
        verify(observer, never()).onError(any(classOf[Exception]))
        verify(observer, times(1)).onCompleted()
    }
    
    @Test def testReduce {
        val observable = Observable.from(1, 2, 3, 4)
        assertEquals(10, observable.reduce((a: Int, b: Int) => a + b).toBlockingObservable.single)
    }
    
    @Test def testSkip {
        val observable = Observable.from(1, 2, 3, 4)
        val skipped = observable.skip(2)
        assertSubscribeReceives(skipped)(3, 4)
    }
    
    /**
     * Both testTake and testTakeWhileWithIndex exposed a bug with unsubscribes not properly propagating.
     * observable.take(2) produces onNext(first), onNext(second), and 4 onCompleteds
     * it should produce onNext(first), onNext(second), and 1 onCompleted
     *
     * Switching to Observable.create(OperationTake.take(observable, 2)) works as expected
     */
    @Test def testTake {
        import rx.operators._
        
        val observable = Observable.from(1, 2, 3, 4, 5)
        val took = Observable.create(OperationTake.take(observable, 2))
        assertSubscribeReceives(took)(1, 2)
    }
    
    @Test def testTakeWhile {
        val observable = Observable.from(1, 3, 5, 6, 7, 9, 11)
        val took = observable.takeWhile(isOdd)
        assertSubscribeReceives(took)(1, 3, 5)
    }
    
    /*@Test def testTakeWhileWithIndex {
     val observable = Observable.from(1, 3, 5, 6, 7, 9, 11, 12, 13, 15, 17)
     val took = observable.takeWhileWithIndex((i: Int, idx: Int) => isOdd(i) && idx > 4)
     assertSubscribeReceives(took)(9, 11)
     }*/
    
    @Test def testTakeLast {
        val observable = Observable.from(1, 2, 3, 4, 5, 6, 7, 8, 9)
        val tookLast = observable.takeLast(3)
        assertSubscribeReceives(tookLast)(7, 8, 9)
    }
    
    @Test def testToList {
        val observable = Observable.from(1, 2, 3, 4)
        val toList = observable.toList
        assertSubscribeReceives(toList)(List(1, 2, 3, 4).asJava)
    }
    
    @Test def testToSortedList {
        val observable = Observable.from(1, 3, 4, 2)
        val toSortedList = observable.toSortedList
        assertSubscribeReceives(toSortedList)(List(1, 2, 3, 4).asJava)
    }
    
    @Test def testToArbitrarySortedList {
        val observable = Observable.from("a", "aaa", "aaaa", "aa")
        val sortByLength = (s1: String, s2: String) => s1.length.compareTo(s2.length)
        val toSortedList = observable.toSortedList(sortByLength)
        assertSubscribeReceives(toSortedList)(List("a", "aa", "aaa", "aaaa").asJava)
    }
    
    @Test def testToIterable {
        val observable = Observable.from(1, 2)
        val it = observable.toBlockingObservable.toIterable.iterator
        assertTrue(it.hasNext)
        assertEquals(1, it.next)
        assertTrue(it.hasNext)
        assertEquals(2, it.next)
        assertFalse(it.hasNext)
    }
    
    @Test def testStartWith {
        val observable = Observable.from(1, 2, 3, 4)
        val newStart = observable.startWith(-1, 0)
        assertSubscribeReceives(newStart)(-1, 0, 1, 2, 3, 4)
    }
    
    @Test def testOneLineForComprehension {
        val mappedObservable = for {
            i: Int <- Observable.from(1, 2, 3, 4)
        } yield i + 1
        assertSubscribeReceives(mappedObservable)(2, 3, 4, 5)
        assertFalse(mappedObservable.isInstanceOf[ScalaObservable[_]])
    }
    
    @Test def testSimpleMultiLineForComprehension {
        val flatMappedObservable = for {
            i: Int <- Observable.from(1, 2, 3, 4)
            j: Int <- Observable.from(1, 10, 100, 1000)
        } yield i + j
        assertSubscribeReceives(flatMappedObservable)(2, 12, 103, 1004)
        assertFalse(flatMappedObservable.isInstanceOf[ScalaObservable[_]])
    }
    
    @Test def testMultiLineForComprehension {
        val doubler = (i: Int) => Observable.from(i, i)
        val flatMappedObservable = for {
            i: Int <- Observable.from(1, 2, 3, 4)
            j: Int <- doubler(i)
        } yield j
        //can't use assertSubscribeReceives since each number comes in 2x
        flatMappedObservable.subscribe(observer.asInstanceOf[Observer[Int]])
        List(1, 2, 3, 4).foreach(i => verify(observer, times(2)).onNext(i))
        verify(observer, never()).onError(any(classOf[Exception]))
        verify(observer, times(1)).onCompleted()
        assertFalse(flatMappedObservable.isInstanceOf[ScalaObservable[_]])
    }
    
    @Test def testFilterInForComprehension {
        val doubler = (i: Int) => Observable.from(i, i)
        val filteredObservable = for {
            i: Int <- Observable.from(1, 2, 3, 4)
            j: Int <- doubler(i) if isOdd(i)
        } yield j
        //can't use assertSubscribeReceives since each number comes in 2x
        filteredObservable.subscribe(observer.asInstanceOf[Observer[Int]])
        List(1, 3).foreach(i => verify(observer, times(2)).onNext(i))
        verify(observer, never()).onError(any(classOf[Exception]))
        verify(observer, times(1)).onCompleted()
        assertFalse(filteredObservable.isInstanceOf[ScalaObservable[_]])
    }
    
    @Test def testForEachForComprehension {
        val doubler = (i: Int) => Observable.from(i, i)
        val intBuffer = ArrayBuffer.empty[Int]
        val forEachComprehension = for {
            i: Int <- Observable.from(1, 2, 3, 4)
            j: Int <- doubler(i) if isEven(i)
        } {
            intBuffer.append(j)
        }
        assertEquals(List(2, 2, 4, 4), intBuffer.toList)
    }
    
    private def assertSubscribeReceives[T](o: Observable[T])(values: T*) = {
        o.subscribe(observer.asInstanceOf[Observer[T]])
        values.toList.foreach(t => verify(observer, times(1)).onNext(t))
        verify(observer, never()).onError(any(classOf[Exception]))
        verify(observer, times(1)).onCompleted()
    }

}


