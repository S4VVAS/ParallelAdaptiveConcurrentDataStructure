package savvas;

import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;

public class ConcurrentAddRemoveLog<E> {

	private ConcurrentLinkedDeque<E> add = new ConcurrentLinkedDeque<E>();
	private ConcurrentLinkedDeque<E> remove = new ConcurrentLinkedDeque<E>();

	private ConcurrentLinkedDeque<E> addTemp = new ConcurrentLinkedDeque<E>();
	private ConcurrentLinkedDeque<E> removeTemp = new ConcurrentLinkedDeque<E>();

	AtomicBoolean addUpdate = new AtomicBoolean(false);
	AtomicBoolean removeUpdate = new AtomicBoolean(false);

	private State addLogState = State.INACTIVE;
	private State removeLogState = State.INACTIVE;

	private enum State {
		INACTIVE, ACTIVE
	}

	public void remove(E elm) {

		switch (removeLogState) {
		case INACTIVE:
			logRemove(elm);
			break;
		case ACTIVE: // When active, we use temp for removes
			removeTemp.push(elm);
			break;
		}
	}

	public void add(E elm) {
		switch (addLogState) {
		case INACTIVE:
			logAdd(elm);
			break;
		case ACTIVE: // When active, we use temp for removes
			addTemp.push(elm);
			break;
		}
	}

	private void logRemove(E elm) {
		if (add.contains(elm))
			add.remove(elm);
		if (!remove.contains(elm))
			remove.push(elm);
	}

	private void logAdd(E elm) {
		if (remove.contains(elm))
			remove.remove(elm);
		if (!add.contains(elm)) {
			add.push(elm);
		}

	}

	private boolean isRemoved(E elm) {
		return remove.contains(elm);
	}

	private boolean isAdded(E elm) {
		return add.contains(elm);
	}

	private boolean hasLoggedItems() {
		return !add.isEmpty() || !remove.isEmpty();
	}

	public ConcurrentLinkedDeque<E> getAndClearAddLog() { // Returns null if unsuccessful/ no log items available
		if (!addUpdate.compareAndExchange(false, true)) { // compareAndExchange: Returns false if it succeeds to change
															// it to true.
			if (!add.isEmpty()) {
				addLogState = State.ACTIVE;
				ConcurrentLinkedDeque<E> addC = add;
				add.clear();
				addLogState = State.INACTIVE;

				addC.addAll(addTemp);
				addTemp.clear();
				addUpdate.set(false);
				return addC;
			}
			addUpdate.set(false);
		}
		return null;
	}

	public ConcurrentLinkedDeque<E> getAndClearRemoveLog() { // Returns null if unsuccessful/ no log items available
		if (!removeUpdate.compareAndExchange(false, true)) { // compareAndExchange: Returns false if it succeeds to
																// change it to true.
			if (!remove.isEmpty()) {
				removeLogState = State.ACTIVE;
				ConcurrentLinkedDeque<E> addC = add;
				remove.clear();
				removeLogState = State.INACTIVE;

				addC.addAll(addTemp);
				addTemp.clear();
				removeUpdate.set(false);
				return addC;
			}
			removeUpdate.set(false);
		}
		return null;
	}

}
