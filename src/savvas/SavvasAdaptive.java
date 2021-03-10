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

public class SavvasAdaptive<E> implements Iterable<E> {

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

	private ConcurrentHashMap<E, E> changeLog = new ConcurrentHashMap<E, E>();
	private ConcurrentHashMap<E, E> removeLog = new ConcurrentHashMap<E, E>();
	private boolean logIsActive = false;

	private Evaluator evaluator = new Evaluator();
	private Logger logger = new Logger();
	private Thread evalThread;

	public SavvasAdaptive() {
		this(State.LIST, true);
	}

	public SavvasAdaptive(State state) {
		this(state, true);
	}

	public SavvasAdaptive(State state, boolean switchable) {
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

	public void add(E element) { // WRITE-OPERATION-----------------------------------------------------------------------------------------------

		if (logIsActive)
			logAdd(element);
		else {
			//lock.readLock().lock();
			switch (currentState) {
			case LIST:
				list.add(element);
				break;
			case MAP:
				map.put(element, element);
				break;
			default:
				throw new RuntimeException("Invalid internal state");
			}
			//lock.readLock().unlock();
		}
		countOperation(OperationType.UPDATE);
	}

	public void remove(E element) { // WRITE-OPERATION-----------------------------------------------------------------------------------------------
		if (logIsActive)
			logRemove(element);
		else {
			//lock.readLock().lock();
			switch (currentState) {
			case LIST:
				list.remove(element);
				break;
			case MAP:
				map.remove(element);
				break;
			default:
				throw new RuntimeException("Invalid internal state");
			}
			//lock.readLock().unlock();
		}
		countOperation(OperationType.UPDATE);
	}

	public boolean contains(E element) {
		if (logIsActive && logRead(element) != null) //Check log, if not in log check DS
			return true;
		
		Boolean b;
		switch (currentState) {
		case LIST:
			b = list.contains(element);
			break;
		case MAP:
			b = map.containsKey(element);
			break;
		default:
			throw new RuntimeException("Invalid internal state");
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
			throw new RuntimeException("Invalid internal state");
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
	
	
	
	private void logRemove(E elm) {
		if (changeLog.containsKey(elm))
			changeLog.remove(elm);
		removeLog.put(elm, elm);
	}

	private void logAdd(E elm) {
		if (removeLog.containsKey(elm))
			removeLog.remove(elm);
		changeLog.put(elm, elm);
	}

	private E logRead(E elm) {
		if(removeLog.contains(elm))
			return null;
		return changeLog.get(elm);
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

	private class Logger implements Runnable {

		@Override
		public void run() {

		}

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
