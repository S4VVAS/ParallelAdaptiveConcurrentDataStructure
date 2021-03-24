package savvas;

import java.util.Arrays;
import java.util.Iterator;
import java.util.stream.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.Map;
import java.util.Optional;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class SavvasLogAdaptive<E> implements Iterable<E> {

	private static final long SWITCH_THRESHOLD = 100;
	private int threads = 100;

	public enum State {
		LIST, MAP
	}

	private enum LogState {
		INACTIVE, ACTIVE, RELEASE
	}

	private enum OperationType {
		UPDATE, ITERATE, READ
	}

	// HAVE DIFFERENT CLASS INSTANCES DEPPENDING ON STATE. CLASSES CONTAIN THE
	// DIFFERENT STATE DEPPENDING METHODS (ELIMINATES IF STATEMENTS)
	// SWTICH BETWEEN INSTANCES WHEN SWITCHING STATE. (METHODS MUST HAVE COMMON
	// NAMES AND PARAMETERS)

	AtomicInteger operation = new AtomicInteger(0);

	private CopyOnWriteArrayList<E> list = new CopyOnWriteArrayList<E>();
	private ConcurrentHashMap<E, E> map = new ConcurrentHashMap<E, E>();
	private State currentState;
	private boolean switchable;
	private boolean isSwitched = false;

	private ConcurrentLinkedDeque<E> addLog = new ConcurrentLinkedDeque<E>();
	private ConcurrentLinkedDeque<E> removeLog = new ConcurrentLinkedDeque<E>();
	private LogState logstate;

	private Evaluator evaluator = new Evaluator();
	private Thread evalThread;

	private ForkJoinPool threadPool = new ForkJoinPool(100);

	public SavvasLogAdaptive() {
		this(State.LIST, true);
	}

	public SavvasLogAdaptive(State state) {
		this(state, true);
	}

	public SavvasLogAdaptive(State state, boolean switchable) {
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

	public void add(E element) { // WRITE-OPERATION-----------------------------------------------------------------------------------------------

		switch (logstate) {
		case INACTIVE:
			addElement(element);
			break;
		case ACTIVE:
			logAdd(element);
			break;
		case RELEASE:
			if (removeLog.contains(element))
				removeLog.remove(element);
			else if (addLog.contains(element))
				addLog.remove(element);
			addElement(element);
			break;
		}
		countOperation(OperationType.UPDATE);
	}

	private void addElement(E element) {
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
	}

	public void remove(E element) { // WRITE-OPERATION-----------------------------------------------------------------------------------------------
		switch (logstate) {
		case INACTIVE:
			removeElement(element);
			break;
		case ACTIVE:
			logRemove(element);
			break;
		case RELEASE:
			if (removeLog.contains(element))
				removeLog.remove(element);
			else if (addLog.contains(element))
				addLog.remove(element);
			removeElement(element);
			break;
		}

		countOperation(OperationType.UPDATE);
	}

	private void removeElement(E element) {
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
	}

	public boolean contains(E element) {
		Boolean b = false;

		if (logstate != LogState.INACTIVE) // Check log, if not in log check DS
			b = logContains(element);

		if (!b) {
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
		logstate = LogState.ACTIVE;

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
		logstate = LogState.RELEASE;
		applyAddLog();
		applyRemoveLog();
		logstate = LogState.INACTIVE;
	}

	private void applyAddLog() {
		E elm = addLog.poll();
		if (elm == null)
			return;
		else {
			addElement(elm);
			applyAddLog();
		}
	}

	private void applyRemoveLog() {
		E elm = removeLog.poll();
		if (elm == null)
			return;
		else {
			removeElement(elm);
			applyRemoveLog();
		}
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
		if (addLog.contains(elm))
			addLog.remove(elm);
		removeLog.push(elm);
	}

	private void logAdd(E elm) {
		if (removeLog.contains(elm))
			removeLog.remove(elm);
		if (!addLog.contains(elm))
			addLog.push(elm);
	}

	private boolean logContains(E elm) {
		if (removeLog.contains(elm))
			return false;
		return addLog.contains(elm);
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
