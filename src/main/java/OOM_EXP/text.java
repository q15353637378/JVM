package OOM_EXP;

import net.sf.cglib.proxy.Enhancer;
import net.sf.cglib.proxy.MethodInterceptor;
import net.sf.cglib.proxy.MethodProxy;
import org.junit.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;

/**
 * @Author qinsicheng
 * @Description 内容：测试不同情况下爆出OOM
 * @Date 19/05/2022 10:10
 */
public class text {

    static class HeapOOM{}

    private static int stackLength = 1 ;

    static  void stackLeak() {
        stackLength++;
        stackLeak();
    }

    /**
     * vm args: -Xms20m -Xmx20m -XX:+HeapDumpOnOutOfMemoryError
     * 限制java堆的大小为20MB，不可扩展
     * -XX:+HeapDumpOnOutOfMemoryError 可以让虚拟机出现内存溢出异常时Dump出当时的内存堆
     * 转储快照以便进行事后分析
     */
    @Test
    public void heap() {
        ArrayList<HeapOOM> list = new ArrayList<>();
        while (true) {
            list.add(new HeapOOM());
        }
    }

    /**
     * 虚拟机栈中出现的异常分为，超出栈限制的最大深度，抛出StackOverFlowError
     * 如果允许栈内存动态拓展，则会在无法申请到更多内存时，抛出OOM
     * HotSpot虚拟机选择的是不支持扩展，所以多数是抛出StackOverFlowError
     * 在单线程下，可能会因为递归不断地插入新的栈帧而将栈挤爆
     * 在多线程下，也可能单个线程的栈还没有溢出，申请线程太多也会造成内存溢出OOM
     * vm args：-Xss限制栈内容量
     */
    @Test
    public void stack() {
        try {
            stackLeak();
        } catch (Throwable e) {
            System.out.println("stack length : "+stackLength);
            throw e;
        }
    }

    /**
     * 在方法区和运行时常量池溢出
     * 通过String.inter()方法，该方法会先从字符串常量区寻找该对象，如果有直接返回，如果没有则创建并返回
     * JDK6以前 设置vm args：-XX:PermSize=6m -XX:MaxPermSize=6m 控制方法区大小 会爆出：OOM：PermGen space，
     * 说明这个时候的字符串常量池是在方法区中
     * JDK7以后 设置上述参数已经没有用了，字符串常量池被放在堆空间中，只能通过设置-Xmx参数限制堆大小可反应出来
     * vm args: -Xmx6m  爆出错误：OutOfMemoryError:GC overhead limit exceeded，这个代表当前已经没有可用内存了，经过多次GC之后仍然没有有效释放内存
     * -Xmx6m -XX:-UseGCOverheadLimit   爆出错误：OutOfMemoryError: Java heap space  说明字符串常量池已经转移到了堆空间中
     */
    @Test
    public void permGenSpace() {
        HashSet<String> set = new HashSet<>();
        int i = 1;
        while (true) {
            set.add(String.valueOf(i++).intern());
        }
    }

    /**
     * 这个是String.intern()经典的问题
     * 当JDK6中得到的结果是两个false
     *      因为JDK6中 intern()方法将首次遇到的字符串实例复制到永久代的字符串常量池中存储，返回的也是字符串常量池的引用
     *      而StringBuilder创建的对象是在堆上面的，所有两者的地址当然不同
     *      而在JDK7中 inter()方法就不需要再拷贝到字符串实例到常量池中了，字符串常量池已经在堆中了，只需要在字符串常量中记录一下实例引用就可以了
     *      而关于java的intern()爆出false，是因为在类加载的时候就有一个静态变量加入到字符串常量池了，所以后面再创建的就 不是同一个对象了
     *      System  --->  initializeSystemClass()  --->   sun.misc.Version.init();  其中Version类在加载的时候创建了一个静态变量
     *      private static final String launcher_name = "java";
     */
    @Test
    public void stringInternTest(){
        String str1 = new StringBuilder("计算机").append("软件").toString();
        System.out.println(str1 == str1.intern());

        String str2 = new StringBuilder("ja").append("va").toString();
        System.out.println(str2 == str2.intern());
    }

    /**
     * 关于方法去的内存溢出，注意：JDK6及其以前，方法区也被承认永久代，因为hotspot团队将堆空间的分代思想来构造方法区
     * 这样，就不用在针对方法区构建代码了，但是后来从JDk7开始将一些内容从方法区中抽离，而JDK完全使用元空间来代替永久代
     * 而一般类加载信息就会放到这部分中，我们可以通过不断地加载新的类，造成OOM
     * 这一块儿我们使用动态代理不断地创建新的类，需要注意的是这里并非纯粹的实验，许多主流的框架例如Spring都会使用到动态代理
     * 来创建新的代理类，而增强类越多，方法区就要越大，这样需要注意可能导致方法区的OOM
     * JDK7使用  JDK8中该指令已经remove了
     * vm args: -XX:PermSize=10M -XX:MaxPermSize=10M
     * JKD8使用
     * -XX:MaxMetaspaceSize:设置元空间的最大值，默认是-1，即不限制
     * -XX:MetaspaceSize:指定元空间的初始空间大小，以字节为单位，当触及该值时，进行垃圾回收
     *      ，同时收集器对该值进行调整，如果释放大量空间则将该值降低，如果释放很少的话，则会在最大值下相应提升
     * -XX:MinMetaspaceFreeRatio:作用是在垃圾回收之后控制最小的元空间剩余容量的百分比，可减少因为元空间的不足导致的
     *      垃圾回收的频率
     * -XX:MaxMetaspaceFreeRatio:控制对最大的元空间剩余容量的百分比
     *
     * Exception in thread "main" java.lang.OutOfMemoryError: Metaspace
     */
    @Test
    public void methodArea() {
        while (true) {
            Enhancer enhancer = new Enhancer();
            enhancer.setSuperclass(OOMObject.class);
            enhancer.setUseCache(false);
            enhancer.setCallback(new MethodInterceptor() {
                @Override
                public Object intercept(Object o, Method method, Object[] objects, MethodProxy methodProxy) throws Throwable {
                    return methodProxy.invokeSuper(objects,objects);
                }
            });
            enhancer.create();
        }
    }
    static class OOMObject{}

    /**
     * 直接内存，不属于运行时数据区，但这部分内存被频繁使用，而在JKD 1.4后加入了NIO
     * 它可以使用Native函数库直接分配堆外内存然后通过一个缓存在堆中的DirectByteBuffer对象
     * 作为这块儿内存的引用来操作，能显著的提高性能，因为避免了在Java堆和Native堆中来回的复制数据
     *
     * 容量可通过：-XX:MaxDirectMemorySize参数来设定大小，如果不设定默认使用java堆的最大值（-Xmx指定）
     *
     * java.lang.OutOfMemoryError
     * 	at sun.misc.Unsafe.allocateMemory(Native Method)
     * 	at OOM_EXP.text.DirectMemory(text.java:148)
     *
     * 由直接内存导致的内存溢出，明显特征是在Heap Dump文件中不会看见什么明显的异常情况
     * 如果发现内存溢出后的Dump文件很小，而程序中又间接的使用了DirectMemory（典型的间接使用就是Nio）
     * 那可以着重的检查直接内存方面的原因了
     */
    @Test
    public void DirectMemory() throws Exception{
        Field unsafeField = Unsafe.class.getDeclaredFields()[0];
        unsafeField.setAccessible(true);
        Unsafe unsafe = (Unsafe) unsafeField.get(null);
        while (true) {
            unsafe.allocateMemory(_1MB);
        }
    }
    private static final int _1MB = 1024*1024;

    public static void main(String[] args) {

    }
}
