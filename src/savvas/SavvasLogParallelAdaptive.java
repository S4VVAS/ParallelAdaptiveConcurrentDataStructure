package savvas;

import java.util.Arrays;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicInteger;

public class SavvasLogParallelAdaptive<E> implements Iterable<E> {

	private static final long SWITCH_THRESHOLD = 100;
	private int threads = 100;

	public enum State {
		LIST, MAP
	}
	
	
	private enum OperationType {
		UPDATE, ITERATE, READ
	}

	AtomicInteger operation = new AtomicInteger(0);

	private CopyOnWriteArrayList<E> list = new CopyOnWriteArrayList<E>();
	private ConcurrentHashMap<E, E> map = new ConcurrentHashMap<E, E>();
	private State currentState;
	private boolean switchable;
	private boolean isSwitched = false;

	private E tempVar;
	private boolean switching = false;

	private Evaluator evaluator = new Evaluator();
	private Thread evalThread;

	private ForkJoinPool threadPool = new ForkJoinPool(100);

	public SavvasLogParallelAdaptive() {
		this(State.LIST, true);
	}

	public SavvasLogParallelAdaptive(State state) {
		this(state, true);
	}

	public SavvasLogParallelAdaptive(State state, boolean switchable) {
		currentState = state;
		this.switchable = switchable;

	}

	public void setup(E[] elementList) {
		switch (currentState) {
		case LIST:
			list.addAll(Arrays.asList(elementList));
			break;
		case MAP:
			Arrays.asList(elementList).forEach(v -> map.put(v, v));
			break;
		default:
			throw new RuntimeException("Invalid internal state");
		}
		if (switchable) {
			evalThread = new Thread(evaluator);
			evalThread.start();
		}
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
		int i = -1;
		switch (currentState) {
		case LIST:
			i = list.size();
			break;
		case MAP:
			i = map.size();
			break;
		default:
			throw new RuntimeException("Invalid internal state");
		}
		return i;
	}

	public boolean hasSwitched() {
		return isSwitched;
	}

	public void add(E element) {
		// lock.readLock().lock();
		switch (currentState) {
		case LIST:
			list.add(element);
			break;
		case MAP:
			map.put(element, element);
			break;
		default:
			throw new RuntimeException("Invalid state");
		}
		// lock.readLock().unlock();
		countOperation(OperationType.UPDATE);
	}

	public void remove(E element) {
		// lock.readLock().lock();
		switch (currentState) {
		case LIST:
			list.remove(element);
			break;
		case MAP:
			map.remove(element);
			break;
		default:
			throw new RuntimeException("Invalid state");
		}
		// lock.readLock().unlock();
		countOperation(OperationType.UPDATE);
	}

	public boolean contains(E element) {
		Boolean b;
		switch (currentState) {
		case LIST:
			b = list.contains(element);
			break;
		case MAP:
			b = map.containsKey(element);
			break;
		default:
			throw new RuntimeException("Invalid state");
		}
		countOperation(OperationType.READ);
		return b;
	}

	@Override
	public Iterator<E> iterator() {
		Iterator<E> i;
		switch (currentState) {
		case LIST:
			i = list.iterator();
			break;
		case MAP:
			i = map.values().iterator();
			break;
		default:
			throw new RuntimeException("Invalid internal state");
		}
		countOperation(OperationType.ITERATE);
		return i;
	}

	private void switchDS() {
		// lock.writeLock().lock();
		isSwitched = true;
		switching = true;

		switch (currentState) {
		case LIST:
			System.out.println("=======Switching to map=======");
			map.clear();
			list.forEach(v -> map.put(v, v));
			currentState = State.MAP;
			// list.clear();
			break;
		case MAP:
			System.out.println("=======Switching to list=======");
			list.clear();
			list.addAll(map.values());
			currentState = State.LIST;
			// map.clear();
			break;
		default:
			throw new RuntimeException("Invalid internal state");
		}
		// lock.writeLock().unlock();

		switching = false;
	}

	private void switchToList() {
		Iterator<E> it = map.values().iterator();
		map.forEach(1, null, null);
		
		while(it.hasNext()) {
			tempVar = it.next();
			
			it.remove();
		}
		
		for(E e: map.values()) {
			tempVar = e;
			map.remove(e);
			list.add(e);
		}

	}

	private void switchToMap() {

	}

	private void countOperation(OperationType type) {
		if (switchable) {
			switch (type) {
			case READ:
			case UPDATE:
				operation.decrementAndGet();
				break;
			case ITERATE:
				operation.incrementAndGet();
				break;
			}
		}
	}

	public void setThreads(int threads) {
		this.threads = threads;
		threadPool = new ForkJoinPool(threads > 8 ? 8 : threads);
	}

	public void evaluateSwitch() {
		if ((operation.get() > SWITCH_THRESHOLD && currentState == State.MAP)
				|| (currentState == State.MAP && threads <= 8)) {
			switchDS();
		} else if (operation.get() < -SWITCH_THRESHOLD && currentState == State.LIST && threads > 8) {
			System.out.println("THREADS: " + threads);
			switchDS();
		}
		operation.set(0);
	}

	private class Evaluator implements Runnable {

		@Override
		public void run() {
			while (true) {
				if (Thread.currentThread().isInterrupted())
					break;
				try {
					Thread.sleep(5000);
					evaluateSwitch();
				} catch (InterruptedException e) {
					break;
				}
			}
		}
	}

}
