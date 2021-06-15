package savvas_old;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Collectors;

import savvas.ParallelAdaptive;
import onlineAdaptive.OnlineAdaptiveConcurrentDataStructure;


public class MapTest {
	
	Map<Object, Object> map = new ConcurrentHashMap<Object,Object>();
	CopyOnWriteArrayList<Object> list = new CopyOnWriteArrayList<Object>();
	ConcurrentLinkedDeque<Object> q = new ConcurrentLinkedDeque<Object>();
	
	int sizeOfDS = 8000000;
	
	public double m1() {
		list.clear();
		
		ConcurrentLinkedDeque<Object> list2 = new ConcurrentLinkedDeque<Object>();
		
		for(int i = 0; i < sizeOfDS; i++) {
			list2.add(Integer.toString(i + 6));
		}
		
		double start = System.currentTimeMillis();
		
		list2.push("1");
		list2.push("2");
		list2.push("3");
		list2.push("4");
		list2.push("5");
		
		list2.remove("1");
		list2.remove("2");
		list2.remove("3");
		
		list2.poll();
		list2.poll();
		list2.poll();
		list2.poll();
		list2.poll();
		list2.poll();
		list2.poll();
		list2.poll();
		
		
		//list2.clear();
		
		start = System.currentTimeMillis() - start;
		
		return start;
	}
	
	public double m2() {
		list.clear();
		
		
		ConcurrentLinkedQueue<Object> list2 = new ConcurrentLinkedQueue<Object>();
		for(int i = 0; i < sizeOfDS; i++) {
			list2.add(Integer.toString(i + 6));
		}
		
		double start = System.currentTimeMillis();
		
		list2.offer("1");
		list2.offer("2");
		list2.offer("3");
		list2.offer("4");
		list2.offer("5");
		
		list2.remove("1");
		list2.remove("2");
		list2.remove("3");
		
		list2.poll();
		list2.poll();
		list2.poll();
		list2.poll();
		list2.poll();
		list2.poll();
		list2.poll();
		list2.poll();
		
		//list2.clear();
		
		start = System.currentTimeMillis() - start;
		
		return start;
	}
	
	public double pa() {
		list.clear();
		
		ParallelAdaptive<Object> map2 = new ParallelAdaptive<Object>(ParallelAdaptive.State.LIST, true);
		map2.setThreads(8);
		
		Thread one = new Thread(() -> {for(int i = 0; i < sizeOfDS/8 * 1; i++)map2.add(Integer.toString(i ));});
		Thread two = new Thread(() -> {for(int i = sizeOfDS/8 * 1; i < sizeOfDS/8 * 2; i++)map2.add(Integer.toString(i ));});
		Thread three = new Thread(() -> {for(int i = sizeOfDS/8 * 2; i < sizeOfDS/8 * 3; i++)map2.add(Integer.toString(i ));});
		Thread four = new Thread(() -> {for(int i = sizeOfDS/8 * 3; i < sizeOfDS/8 * 4; i++)map2.add(Integer.toString(i));});
		Thread five = new Thread(() -> {for(int i = sizeOfDS/8 * 4; i < sizeOfDS/8 * 5; i++)map2.add(Integer.toString(i));});
		Thread six = new Thread(() -> {for(int i = sizeOfDS/8 * 5; i < sizeOfDS/8 * 6; i++)map2.add(Integer.toString(i));});
		Thread seven = new Thread(() -> {for(int i = sizeOfDS/8 * 6; i < sizeOfDS/8 * 7; i++)map2.add(Integer.toString(i));});
		Thread eight = new Thread(() -> {for(int i = sizeOfDS/8 * 7; i < sizeOfDS/8 * 8; i++)map2.add(Integer.toString(i));});
		
		Thread one1 = new Thread(() -> {for(int i = 0; i < sizeOfDS/8 * 1; i++)map2.remove(Integer.toString(i ));});
		Thread two1 = new Thread(() -> {for(int i = sizeOfDS/8 * 1; i < sizeOfDS/8 * 2; i++)map2.remove(Integer.toString(i ));});
		Thread three1 = new Thread(() -> {for(int i = sizeOfDS/8 * 2; i < sizeOfDS/8 * 3; i++)map2.remove(Integer.toString(i ));});
		Thread four1 = new Thread(() -> {for(int i = sizeOfDS/8 * 3; i < sizeOfDS/8 * 4; i++)map2.remove(Integer.toString(i));});
		Thread five1 = new Thread(() -> {for(int i = sizeOfDS/8 * 4; i < sizeOfDS/8 * 5; i++)map2.remove(Integer.toString(i));});
		Thread six1 = new Thread(() -> {for(int i = sizeOfDS/8 * 5; i < sizeOfDS/8 * 6; i++)map2.remove(Integer.toString(i));});
		Thread seven1 = new Thread(() -> {for(int i = sizeOfDS/8 * 6; i < sizeOfDS/8 * 7; i++)map2.remove(Integer.toString(i));});
		Thread eight1 = new Thread(() -> {for(int i = sizeOfDS/8 * 7; i < sizeOfDS/8 * 8; i++)map2.remove(Integer.toString(i));});
		
		//double start = System.currentTimeMillis();
		
		one.start();
		two.start();
		three.start();
		four.start();
		five.start();
		six.start();
		seven.start();
		eight.start();
		
		double start = System.currentTimeMillis();
		
		one1.start();
		seven1.start();
		five1.start();
		
		try {
			one.join();
			two.join();
			three.join();
			four.join();
			five.join();
			six.join();
			seven.join();
			eight.join();
			
			one.join();
			seven1.join();
			five1.join();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		
		start = System.currentTimeMillis() - start;
		
		return start;
		
	}
	
	public double oa() {
		list.clear();
		
		OnlineAdaptiveConcurrentDataStructure<Object> map2 = new OnlineAdaptiveConcurrentDataStructure<Object>(OnlineAdaptiveConcurrentDataStructure.State.LIST, true);
		map2.setThreads(8);
		
		
		Thread one = new Thread(() -> {for(int i = 0; i < sizeOfDS/8 * 1; i++)map2.add(Integer.toString(i ));});
		Thread two = new Thread(() -> {for(int i = sizeOfDS/8 * 1; i < sizeOfDS/8 * 2; i++)map2.add(Integer.toString(i ));});
		Thread three = new Thread(() -> {for(int i = sizeOfDS/8 * 2; i < sizeOfDS/8 * 3; i++)map2.add(Integer.toString(i ));});
		Thread four = new Thread(() -> {for(int i = sizeOfDS/8 * 3; i < sizeOfDS/8 * 4; i++)map2.add(Integer.toString(i));});
		Thread five = new Thread(() -> {for(int i = sizeOfDS/8 * 4; i < sizeOfDS/8 * 5; i++)map2.add(Integer.toString(i));});
		Thread six = new Thread(() -> {for(int i = sizeOfDS/8 * 5; i < sizeOfDS/8 * 6; i++)map2.add(Integer.toString(i));});
		Thread seven = new Thread(() -> {for(int i = sizeOfDS/8 * 6; i < sizeOfDS/8 * 7; i++)map2.add(Integer.toString(i));});
		Thread eight = new Thread(() -> {for(int i = sizeOfDS/8 * 7; i < sizeOfDS/8 * 8; i++)map2.add(Integer.toString(i));});
		
		Thread one1 = new Thread(() -> {for(int i = 0; i < sizeOfDS/8 * 1; i++)map2.remove(Integer.toString(i ));});
		Thread two1 = new Thread(() -> {for(int i = sizeOfDS/8 * 1; i < sizeOfDS/8 * 2; i++)map2.remove(Integer.toString(i ));});
		Thread three1 = new Thread(() -> {for(int i = sizeOfDS/8 * 2; i < sizeOfDS/8 * 3; i++)map2.remove(Integer.toString(i ));});
		Thread four1 = new Thread(() -> {for(int i = sizeOfDS/8 * 3; i < sizeOfDS/8 * 4; i++)map2.remove(Integer.toString(i));});
		Thread five1 = new Thread(() -> {for(int i = sizeOfDS/8 * 4; i < sizeOfDS/8 * 5; i++)map2.remove(Integer.toString(i));});
		Thread six1 = new Thread(() -> {for(int i = sizeOfDS/8 * 5; i < sizeOfDS/8 * 6; i++)map2.remove(Integer.toString(i));});
		Thread seven1 = new Thread(() -> {for(int i = sizeOfDS/8 * 6; i < sizeOfDS/8 * 7; i++)map2.remove(Integer.toString(i));});
		Thread eight1 = new Thread(() -> {for(int i = sizeOfDS/8 * 7; i < sizeOfDS/8 * 8; i++)map2.remove(Integer.toString(i));});
		
		//double start = System.currentTimeMillis();
		
		one.start();
		two.start();
		three.start();
		four.start();
		five.start();
		six.start();
		seven.start();
		eight.start();
		
		double start = System.currentTimeMillis();
		
		one1.start();
		seven1.start();
		five1.start();
		
		try {
			one.join();
			two.join();
			three.join();
			four.join();
			five.join();
			six.join();
			seven.join();
			eight.join();
			
			one.join();
			seven1.join();
			five1.join();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		
		start = System.currentTimeMillis() - start;
		
		return start;
		
	}
	
	
	
	/*public double maptomapp() {
		try {
			map = threadPool.submit(() -> elementList.parallelStream().collect(ConcurrentHashMap<E, E>::new, (map, e) -> 
			{map.put(e, e);}, (map, eMp) -> {map.putAll(eMp);})).get();
		} catch (InterruptedException e1) {
			e1.printStackTrace();
		} catch (ExecutionException e1) {
			e1.printStackTrace();
		}
	}*/

	public static void main(String[] args) {
		
		MapTest m = new MapTest();
		int iterations = 1;
		int sizeOfDS = 1;
		
		for(Double i = 0.0; i < sizeOfDS; i++) 
			m.list.add(i);
		
		System.out.println(m.list.size());
		

		Double one = 0.0;
		for(int i = 0; i < iterations; i++) 
			one = one + m.oa();
		one = one/iterations;
		
		
	
		Double two = 0.0;
		for(int i = 0; i < iterations; i++) 
			two = two + m.pa();
		two = two/iterations;
		
		

		
		System.out.println( "For " + iterations + " iterations on a list with " + m.sizeOfDS + " elements");
		System.out.println("OA avg time: " + one  + " ms");
		System.out.println("PA avg time: " + two  + " ms");
		

		
		
		
	}
}
