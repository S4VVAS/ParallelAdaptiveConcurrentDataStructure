package savvas_old;

//Savvas Giortsis

import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;

public class ConcurrentAddRemoveLog<E> {

	private ConcurrentLinkedDeque<E> add = new ConcurrentLinkedDeque<E>();
	private ConcurrentLinkedDeque<E> remove = new ConcurrentLinkedDeque<E>();

	private ConcurrentLinkedDeque<E> addTemp = new ConcurrentLinkedDeque<E>();
	private ConcurrentLinkedDeque<E> removeTemp = new ConcurrentLinkedDeque<E>();

	private AtomicBoolean addUpdate = new AtomicBoolean(false);
	private AtomicBoolean removeUpdate = new AtomicBoolean(false);

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
		case ACTIVE:
			removeTemp.push(elm);
			break;
		}
	}

	public void add(E elm) {
		switch (addLogState) {
		case INACTIVE:
			logAdd(elm);
			break;
		case ACTIVE:
			addTemp.push(elm);
			break;
		}
	}

	public void clear() {
		add.clear();
		remove.clear();
		addTemp.clear();
		removeTemp.clear();
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

	public E pollAddLog() {
		return add.poll();
	}

	public E pollRemoveLog() {
		return remove.poll();
	}

	public boolean isRemoved(E elm) {
		return remove.contains(elm);
	}

	public boolean isAdded(E elm) {
		return add.contains(elm);
	}

	public boolean hasLoggedItems() {
		return !add.isEmpty() || !remove.isEmpty();
	}

	public ConcurrentLinkedDeque<E> getAndClearAddLog() {
		if (!addUpdate.compareAndSet(false, true)) {
			if (!add.isEmpty()) {
				addLogState = State.ACTIVE;
				ConcurrentLinkedDeque<E> addC = add;
				add.clear();
				addLogState = State.INACTIVE;
				addUpdate.set(false);

				addC.addAll(addTemp);
				addTemp.clear();

				return addC;
			}
			addUpdate.set(false);
		}
		return null;
	}

	public ConcurrentLinkedDeque<E> getAndClearRemoveLog() {
		if (!removeUpdate.compareAndSet(false, true)) {
			if (!remove.isEmpty()) {
				removeLogState = State.ACTIVE;
				ConcurrentLinkedDeque<E> addC = add;
				remove.clear();
				removeLogState = State.INACTIVE;
				removeUpdate.set(false);

				addC.addAll(addTemp);
				addTemp.clear();
				return addC;
			}
			removeUpdate.set(false);
		}
		return null;
	}

}
