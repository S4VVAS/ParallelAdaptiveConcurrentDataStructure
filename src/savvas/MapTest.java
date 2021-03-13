package savvas;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Collectors;

public class MapTest {
	
	ConcurrentHashMap<Double,Double> map = new ConcurrentHashMap<Double,Double>();
	CopyOnWriteArrayList<Double> list = new CopyOnWriteArrayList<Double>();
	
	//private ForkJoinPool threadPool = new ForkJoinPool(100);
	
	public double mapToMap() {
		list.clear();
		
		double start = System.currentTimeMillis();
		
		list.forEach(v -> map.put(v, v));
		
		start = System.currentTimeMillis() - start;
		
		return start;
	}
	
	public double mapToMapParallel() {
		list.clear();
		
		double start = System.currentTimeMillis();
		
		Map<Double, Double> newMap = list.stream().parallel().collect(Collectors.toMap(e -> e, e -> e, (Obj1, Obj2) -> Obj1));
		map.putAll(newMap);
		
		start = System.currentTimeMillis() - start;
		
		return start;
		
	}

	public static void main(String[] args) {
		MapTest m = new MapTest();
		int iterations = 10000;
		int sizeOfDS = 3;
		
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
		System.out.println("Non-parallel avg time: " + nonParallel * 1000 + " ms");
		System.out.println("Parallel avg time: " + parallel * 1000 + " ms");
		

		
		
		
	}
}
