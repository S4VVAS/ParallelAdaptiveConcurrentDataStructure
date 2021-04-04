package savvas_old;

import java.util.Arrays;
import java.util.Iterator;
import java.util.stream.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class SavvasConcurrentAdaptive<E> implements Iterable<E> {

	private static final long SWITCH_THRESHOLD = 100;
	private int threads = 100;

	public enum State {
		LIST, MAP
	}

	private enum OperationType {
		UPDATE, ITERATE, READ
	}

	AtomicInteger operation = new AtomicInteger(0);

	private ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
	private ReentrantLock evaluateLock = new ReentrantLock();

	private CopyOnWriteArrayList<E> list;
	private ConcurrentHashMap<E, E> map;
	private State currentState;
	private boolean switchable;
	private boolean isSwitched = false;

	//private Evaluator evaluator = new Evaluator();
	private Thread evalThread;

	public SavvasConcurrentAdaptive() {
		this(State.LIST, true);
	}

	public SavvasConcurrentAdaptive(State state) {
		this(state, true);
	}

	public SavvasConcurrentAdaptive(State state, boolean switchable) {
		list = new CopyOnWriteArrayList<E>();
		map = new ConcurrentHashMap<E, E>();
		currentState = state;
		this.switchable = switchable;
	}

	public void setup(E[] elementList) {

		list.addAll(Arrays.asList(elementList));
		createMap(Arrays.asList(elementList));

	}

	public void stop() {
		try {
			evalThread.interrupt();
		} catch (NullPointerException e) {
		}
	}

	public void clear() {
		operation.set(0);
		list.clear();
		map.clear();
	}

	public int size() {
		return list.size();
	}

	public boolean hasSwitched() {
		return isSwitched;
	}

	public void add(E element) {
		lock.readLock().lock();

		list.add(element);
		map.put(element, element);

		lock.readLock().unlock();
	}

	public void remove(E element) {
		lock.readLock().lock();

		list.remove(element);
		map.remove(element);

		lock.readLock().unlock();
	}

	public boolean contains(E element) {
		return map.containsKey(element);
	}

	@Override
	public Iterator<E> iterator() {
		return list.iterator();
	}

	public void setThreads(int threads) {
		this.threads = threads;
	}

	private void createMap(List<E> elementList) {
		Map<E, E> newMap = elementList.stream().parallel()
				.collect(Collectors.toMap(e -> e, e -> e, (Obj1, Obj2) -> Obj1));
		map.putAll(newMap);
	}

}
