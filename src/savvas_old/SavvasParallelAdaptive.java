package savvas_old;

//Version 3 of SavvasLogAdaptiveV2NoListLog

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import savvas.ConcurrentAddRemoveLogDEQ;

public class SavvasParallelAdaptive<E> implements Iterable<E> {

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

	private ConcurrentAddRemoveLogDEQ<E> switchLog = new ConcurrentAddRemoveLogDEQ<E>();
	private ConcurrentAddRemoveLogDEQ<E> listApplyLog = new ConcurrentAddRemoveLogDEQ<E>();
	private LogState logstate = LogState.INACTIVE;

	private Boolean doNotApplyListLogRemove = false; // This var can only be modified by Eval thread
	private Boolean doNotApplyListLogAdd = false; // This var can only be modified by Eval thread

	private Evaluator evaluator = new Evaluator();
	private Thread evalThread;

	public SavvasParallelAdaptive() {
		this(State.LIST, true);
	}

	public SavvasParallelAdaptive(State state) {
		this(state, true);
	}

	public SavvasParallelAdaptive(State state, boolean switchable) {
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
			switchLog.add(element);
			break;
		case RELEASE:
			switchLog.remove(element);
			addElement(element);
			break;
		}
		countOperation(OperationType.UPDATE);
	}

	private void addElement(E element) {
		switch (currentState) {
		case LIST:
			if (logstate != LogState.RELEASE)
				list.add(element);
			else {
				if (!doNotApplyListLogAdd)
					list.add(element);
				else
					listApplyLog.add(element);
			}
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
			switchLog.remove(element);
			break;
		case RELEASE:
			switchLog.remove(element);
			removeElement(element);
			break;
		}
		countOperation(OperationType.UPDATE);
	}

	private void removeElement(E element) {
		switch (currentState) {
		case LIST:
			if (logstate != LogState.RELEASE)
				list.remove(element);
			else {
				if (!doNotApplyListLogAdd)
					list.remove(element);
				else
					listApplyLog.remove(element);
			}
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
			b = switchLog.isAdded(element);

		if (!b) {
			switch (currentState) {
			case LIST:
				b = list.contains(element);
				if (logstate == LogState.RELEASE && !b) // If b is not found, and log is releasing, check the
														// listApplyLog for added element
					b = listApplyLog.isAdded(element);
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
		isSwitched = true;
		logstate = LogState.ACTIVE;

		switch (currentState) {
		case LIST:
			parallelCreateMap(list);
			currentState = State.MAP;
			list.clear();
			break;
		case MAP:
			list.addAll(map.values());
			currentState = State.LIST;
			map.clear();
			break;
		default:
			throw new RuntimeException("Invalid internal state");
		}
		logstate = LogState.RELEASE;
		if (currentState == State.MAP) {
			applyAddLogMap();
			applyRemoveLogMap();
		} else {
			applyAddLogList();
			applyRemoveLogList();
		}
		logstate = LogState.INACTIVE;
	}

	private void parallelCreateMap(List<E> elementList) {
		Map<E, E> newMap = elementList.stream().parallel()
				.collect(Collectors.toConcurrentMap(e -> e, e -> e, (Obj1, Obj2) -> Obj1));
		map.putAll(newMap);
	}

	private void applyAddLogMap() {
		E elm;
		while (null != (elm = switchLog.pollRemoveLog()))
			addElement(elm);
	}

	private void applyAddLogList() {
		doNotApplyListLogAdd = true;
		E elm;
		while (null != (elm = switchLog.pollRemoveLog()))
			listApplyLog.add(elm);
		doNotApplyListLogAdd = false;
		list.addAll(listApplyLog.getAndClearAddLog());
	}

	private void applyRemoveLogMap() {
		E elm;
		while (null != (elm = switchLog.pollRemoveLog()))
			removeElement(elm);
	}

	private void applyRemoveLogList() {
		doNotApplyListLogRemove = true;
		E elm;
		while (null != (elm = switchLog.pollRemoveLog()))
			listApplyLog.remove(elm);
		doNotApplyListLogRemove = false;
		list.removeAll(listApplyLog.getAndClearRemoveLog());

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
