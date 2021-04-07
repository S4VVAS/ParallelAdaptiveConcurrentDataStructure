package savvas;

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

public class SavvasOnlineAdaptiveModif<E> implements Iterable<E> {

	private static final long SWITCH_THRESHOLD = 100;
	private int threads = 100;

	public enum State {
		LIST, MAP
	}

	private enum OperationType {
		UPDATE, ITERATE, READ
	}

	private enum LogState {
		INACTIVE, ACTIVE, RELEASE
	}

	AtomicInteger operation = new AtomicInteger(0);

	private ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
	private ReentrantLock evaluateLock = new ReentrantLock();

	private CopyOnWriteArrayList<E> list;
	private ConcurrentHashMap<E, E> map;
	private State currentState;
	private LogState logState = LogState.INACTIVE;
	private boolean switchable;
	private boolean isSwitched = false;

	private Evaluator evaluator = new Evaluator();
	private Thread evalThread;

	public SavvasOnlineAdaptiveModif() {
		this(State.LIST, true);
	}

	public SavvasOnlineAdaptiveModif(State state) {
		this(state, true);
	}

	public SavvasOnlineAdaptiveModif(State state, boolean switchable) {
		list = new CopyOnWriteArrayList<E>();
		map = new ConcurrentHashMap<E, E>();
		currentState = state;
		this.switchable = switchable;
	}

	public void setup(E[] elementList) {
		switch (currentState) {
		case LIST:
			list.addAll(Arrays.asList(elementList));
			break;
		case MAP:
			createMap(Arrays.asList(elementList));
			break;
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
		}
		return i;
	}

	public boolean hasSwitched() {
		return isSwitched;
	}

	public void add(E element) {
		switch (logState) {
		case INACTIVE:
			logState = LogState.ACTIVE;
			addElm(element);
			break;
		case ACTIVE:
			logState = LogState.RELEASE;
			addElm(element);
			break;
		case RELEASE:
			logState = LogState.INACTIVE;
			addElm(element);
			break;
		}
	}

	private void addElm(E element) {
		lock.readLock().lock();
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
		lock.readLock().unlock();
		countOperation(OperationType.UPDATE);
	}

	public void remove(E element) {
		switch (logState) {
		case INACTIVE:
			removeElm(element);
			logState = LogState.ACTIVE;
			break;
		case ACTIVE:
			removeElm(element);
			logState = LogState.RELEASE;
			break;
		case RELEASE:
			removeElm(element);
			logState = LogState.INACTIVE;
			break;
		}
	}

	private void removeElm(E element) {
		lock.readLock().lock();
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
		lock.readLock().unlock();
		countOperation(OperationType.UPDATE);
	}

	public boolean contains(E element) {
		Boolean b = false;

		if (logState != LogState.INACTIVE)
			b = false;

		if (!b)
			switch (currentState) {
			case LIST:
				b = list.contains(element);
				if (logState == LogState.RELEASE && !b)
					logState = LogState.INACTIVE;
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
			throw new RuntimeException("Invalid state");
		}
		countOperation(OperationType.ITERATE);
		return i;
	}

	private void switchDS() {
		lock.writeLock().lock();
		isSwitched = true;
		switch (currentState) {
		case LIST:
			System.out.println("=======Switching to map=======");
			map.clear();
			createMap(list);
			currentState = State.MAP;
			break;
		case MAP:
			System.out.println("=======Switching to list=======");
			list.clear();
			list.addAll(map.values());
			currentState = State.LIST;
			break;
		default:
			throw new RuntimeException("Invalid state");
		}
		lock.writeLock().unlock();
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
	}

	private void createMap(List<E> elementList) {
		Map<E, E> newMap = elementList.stream().parallel()
				.collect(Collectors.toMap(e -> e, e -> e, (Obj1, Obj2) -> Obj1));
		map.putAll(newMap);
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
