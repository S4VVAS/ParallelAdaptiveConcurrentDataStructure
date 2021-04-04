package savvas_old;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Collectors;

public class MapTest {
	
	Map<Object, Object> map = new ConcurrentHashMap<Object,Object>();
	CopyOnWriteArrayList<Object> list = new CopyOnWriteArrayList<Object>();
	
	private ForkJoinPool threadPool = new ForkJoinPool(100);
	
	public double mapToMap() {
		list.clear();
		
		double start = System.currentTimeMillis();
		
	

		
		
		//threadPool = new ForkJoinPool(100);
		try {
			map = threadPool.submit(() -> list.parallelStream().collect(ConcurrentHashMap<Object, Object>::new, (map, e) -> 
			{map.put(e, e);}, (map, eMp) -> {map.putAll(eMp);})).get();
		} catch (InterruptedException e1) {
			e1.printStackTrace();
		} catch (ExecutionException e1) {
			e1.printStackTrace();
		}
		
		//threadPool.shutdown();
		
		start = System.currentTimeMillis() - start;
		
		return start;
	}
	
	public double mapToMapParallel() {
		list.clear();
		
		double start = System.currentTimeMillis();
		
		map = list.stream().parallel().collect(Collectors.toMap(e -> e, e -> e, (Obj1, Obj2) -> Obj1));

		
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
		
		int ir = 100;
		
		while(0 < (ir = ir - 1)) {
			System.out.println(ir);
		}
		System.out.println("HEHHE");
		
		
		MapTest m = new MapTest();
		int iterations = 100000;
		int sizeOfDS = 300000;
		
		for(Double i = 0.0; i < sizeOfDS; i++) 
			m.list.add(i);
		
		
	
		Double nonParallel = 0.0;
		for(int i = 0; i < iterations; i++) 
			nonParallel = nonParallel + m.mapToMap();
		nonParallel = nonParallel/iterations;
		
		
		Double parallel = 0.0;
		for(int i = 0; i < iterations; i++) 
			parallel = parallel + m.mapToMapParallel();
		parallel = parallel/iterations;
		
		

		
		System.out.println( "For " + iterations + " iterations on a list with " + sizeOfDS + " elements");
		System.out.println("ThreadPool avg time: " + nonParallel * 1000 + " ms");
		System.out.println("Parallel avg time: " + parallel * 1000 + " ms");
		

		
		
		
	}
}
