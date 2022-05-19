package Thread_Security_And_LOCK;

import org.junit.Test;

import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicStampedReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @Author qinsicheng
 * @Description 内容：并发中控制线程安全
 * @Date 17/05/2022 08:32
 */
public class test1 {
    static Vector<Integer> vector = new Vector<>();
    static AtomicInteger race = new AtomicInteger(0);
    static final int Thread_Count = 20;

    public static void main(String[] args) {
    }

    /**
     *      这里我们可以看到尽管 vector的get(),add(),remove()方法使用了Synchronize来加锁，但在多线程环境中仍不安全，
     * 当一个线程刚将某一位删除后，令一个线程对该位进行读取就会导致ArrayIndexOutOfBoundException
     * 也可能读取到错误数据，remove底层调用的是System.arrayCopy()
     *      如果让Vector一定做到绝对的线程安全，必须在内部维护一组一致性快照访问，每次对其中元素进行改动都要创建新的快照
     * 但付出的时间和空间成本也是巨大的
     */
    @Test
    public void Vector_Test() {
        while (true) {
            for (int i = 0; i < 10; i++) {
                vector.add(i);
            }
            Thread a = new Thread(() -> {
                //通过在内部加锁的方式  来避免越界报错问题
                synchronized (vector) {
                    for (int i = 0; i < vector.size(); i++) {
                        vector.remove(i);
                    }
                }
            }, "A");
            Thread b = null;
            try {
                b = new Thread(() -> {
                    synchronized (vector) {
                        for (int i = 0; i < vector.size(); i++) {
                            System.out.println(vector.get(i));
                        }
                    }
                }, "B");
            } catch (Exception e) {
                e.printStackTrace();
                break;
            }
            a.start();
            b.start();

            while (Thread.activeCount()>20);
        }
    }

    /**
     * 通过引入原子引用来控制线程竞争，本质上就是CAS，但会导致ABA问题
     * 解决ABA问题，就可以使用AtomicStampedReference,也就是修改原数据的同时，修改版本号，不管数据怎么变，版本是
     * 一直向前的，不过这个类挺鸡肋的。
     * 建议：在lock和synchronize都可以解决问题的情况下，优先选择synchronize，因为性能方面是差不多的，但是加锁解锁，synchronize
     * 会更简单和安全的。
     */
    @Test
    public void  AtomicTest() {
        Thread[] threads = new Thread[Thread_Count];
        for (int i = 0; i < Thread_Count; i++) {
            threads[i] = new Thread(()->{
                for (int i1 = 0; i1 < 10000; i1++) {
                    race.incrementAndGet();
                }
            });
            threads[i].start();
        }
        while (Thread.activeCount()>1) {
            Thread.yield();
            System.out.println(race);
        }

    }

    /**
     * String是一个不可变对象，每次对String对象的操作都会返回一个新的对象
     * javac编译器会对String连接做自动优化，
     * JDK5前是StringBuffer.append()  是线程安全的
     * JDK5后是StringBuilder.append() 虽然线程不安全，但是经过分析sb的所有引用永远不会逃逸到方法外
     *                                  其他线程无法访问到，所以这里虽然有锁，但可以被安全的消除掉，
     *                                  在解释执行时仍会加锁，但即时编译后，代码会忽略所有同步错失而直接执行。
     * @param s1
     * @param s2
     * @param s3
     * @return
     */
    @Test
    public String concatString(String s1,String s2,String s3) {
        return s1+s2+s3;   //两者是等价的
//        StringBuilder sb = new StringBuilder();
//        sb.append(s1);
//        sb.append(s2);
//        sb.append(s3);
//        return sb.toString();
    }
}
