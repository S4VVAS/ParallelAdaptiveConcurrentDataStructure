package savvas;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class ConcurrentAddRemoveLogQ<E> {

	private ConcurrentLinkedQueue<E> add = new ConcurrentLinkedQueue<E>();
	private ConcurrentLinkedQueue<E> remove = new ConcurrentLinkedQueue<E>();

	private ConcurrentLinkedQueue<E> addTemp = new ConcurrentLinkedQueue<E>();
	private ConcurrentLinkedQueue<E> removeTemp = new ConcurrentLinkedQueue<E>();

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
		case ACTIVE: // When active, we use removeTemp for removes
			removeTemp.offer(elm);
			break;
		}
	}

	public void add(E elm) {
		switch (addLogState) {
		case INACTIVE:
			logAdd(elm);
			break;
		case ACTIVE: // When active, we use addTemp for adds
			addTemp.offer(elm);
			break;
		}
	}
	
	public void clear(){
		add.clear();
		remove.clear();
		addTemp.clear();
		removeTemp.clear();
	}

	private void logRemove(E elm) {
		if (add.contains(elm))
			add.remove(elm);
		if (!remove.contains(elm))
			remove.offer(elm);
	}

	private void logAdd(E elm) {
		if (remove.contains(elm))
			remove.remove(elm);
		if (!add.contains(elm)) {
			add.offer(elm);
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

	public ConcurrentLinkedQueue<E> getAndClearAddLog() { // Returns null if unsuccessful/ no log items available
		if (!addUpdate.compareAndSet(false, true)) { // compareAndExchange: Returns false if it succeeds to change (J8 compareAndSet)
															// it to true.
			if (!add.isEmpty()) {
				addLogState = State.ACTIVE;
				ConcurrentLinkedQueue<E> addC = add;
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

	public ConcurrentLinkedQueue<E> getAndClearRemoveLog() { // Returns null if unsuccessful/ no log items available
		if (!removeUpdate.compareAndSet(false, true)) { // compareAndExchange: Returns false if it succeeds to
																// change it to true. (J8 compareAndSet)
			if (!remove.isEmpty()) {
				removeLogState = State.ACTIVE;
				ConcurrentLinkedQueue<E> addC = add;
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
