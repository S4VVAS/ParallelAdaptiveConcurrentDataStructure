package savvas;

//Savvas Giortsis

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import javax.swing.plaf.synth.SynthProgressBarUI;

import savvas_old.ConcurrentAddRemoveLog;

public class ParallelAdaptive<E> implements Iterable<E> {

	private static final long SWITCH_THRESHOLD = 100;
	private int threads = 100;

	public enum State {
		LIST, MAP
	}
	
	private boolean d = Boolean.T

	private enum LogState {
		INACTIVE, ACTIVE, RELEASE
	}

	private enum OperationType {
		UPDATE, ITERATE, READ
	}

	AtomicInteger operation = new AtomicInteger(0);

	private CopyOnWriteArrayList<E> list;
	private ConcurrentHashMap<E, E> map;
	private State currentState;
	private boolean switchable;
	private boolean isSwitched = false;

	private ConcurrentAddRemoveLog<E> switchLog = new ConcurrentAddRemoveLog<E>();
	private ConcurrentAddRemoveLog<E> listApplyLog = new ConcurrentAddRemoveLog<E>();
	private LogState logstate = LogState.INACTIVE;

	private Evaluator evaluator = new Evaluator();
	private Thread evalThread;

	public ParallelAdaptive() {
		this(State.LIST, true);
	}

	public ParallelAdaptive(State state) {
		this(state, true);
	}

	public ParallelAdaptive(State state, boolean switchable) {
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
			parallelCreateMap(Arrays.asList(elementList));
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
		switchLog.clear();
		listApplyLog.clear();
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
			list.add(element);
			break;
		case MAP:
			map.put(element, element);
			break;
		default:
			throw new RuntimeException("Invalid internal state");
		}
	}

	public void remove(E element) {
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
		if (logstate != LogState.INACTIVE)
			b = switchLog.isAdded(element);

		if (!b) {
			switch (currentState) {
			case LIST:
				b = list.contains(element);
				if (logstate == LogState.RELEASE && !b)
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

			applyAddLogMap();
			applyRemoveLogMap();

			break;
		case MAP:
			list.addAll(map.values());
			currentState = State.LIST;
			map.clear();

			applyAddLogList();
			applyRemoveLogList();

			break;
		default:
			throw new RuntimeException("Invalid internal state");
		}
	}

	private void parallelCreateMap(List<E> elementList) {
		Map<E, E> newMap = elementList.stream().parallel()
				.collect(Collectors.toConcurrentMap(e -> e, e -> e, (Obj1, Obj2) -> Obj1));
		map.putAll(newMap);
	}

	private void applyAddLogMap() {
		logstate = LogState.RELEASE;
		E elm;
		while (null != (elm = switchLog.pollRemoveLog()))
			addElement(elm);
	}

	private void applyAddLogList() {
		logstate = LogState.RELEASE;
		E elm;
		while (null != (elm = switchLog.pollRemoveLog()))
			listApplyLog.add(elm);
		ConcurrentLinkedDeque<E> addLog = listApplyLog.getAndClearAddLog();
		if (addLog != null)
			list.addAll(listApplyLog.getAndClearAddLog());
	}

	private void applyRemoveLogMap() {
		E elm;
		while (null != (elm = switchLog.pollRemoveLog()))
			removeElement(elm);
		logstate = LogState.INACTIVE;
	}

	private void applyRemoveLogList() {
		E elm;
		while (null != (elm = switchLog.pollRemoveLog()))
			listApplyLog.remove(elm);
		ConcurrentLinkedDeque<E> remLog = listApplyLog.getAndClearRemoveLog();
		if (remLog != null)
			list.removeAll(remLog);
		logstate = LogState.INACTIVE;
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
