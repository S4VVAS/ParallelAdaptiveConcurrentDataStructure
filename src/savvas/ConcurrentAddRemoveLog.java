package savvas;

import java.util.concurrent.ConcurrentLinkedDeque;

public class ConcurrentAddRemoveLog<E> {

	private ConcurrentLinkedDeque<E> add = new ConcurrentLinkedDeque<E>();
	private ConcurrentLinkedDeque<E> remove = new ConcurrentLinkedDeque<E>();

	private ConcurrentLinkedDeque<E> temp = new ConcurrentLinkedDeque<E>();
	
	private State state = State.INACTIVE;
	private boolean hasChanged = false;

	private enum State {
		INACTIVE, ADDLOG, REMOVELOG
	}

	public void remove(E elm) {

		switch (state) {
		case INACTIVE:
		case ADDLOG:
			logRemove(elm);
			hasChanged = true;
			break;
		case REMOVELOG: // When active, we use temp for removes
			temp.push(elm);
			break;
		}
	}

	public void add(E elm) {
		switch (state) {
		case INACTIVE:
		case REMOVELOG:
			logAdd(elm);
			hasChanged = true;
			break;
		case ADDLOG: // When active, we use temp for removes
			temp.push(elm);
			break;
		}
	}

	public void contains(E elm) {

	}

	private void logRemove(E elm) {
		if (add.contains(elm))
			add.remove(elm);
		remove.push(elm);
	}

	private void logAdd(E elm) {
		if (remove.contains(elm))
			remove.remove(elm);
		if (!add.contains(elm))
			add.push(elm);
	}

	private boolean logContains(E elm) {
		if (remove.contains(elm))
			return false;
		return add.contains(elm);
	}

	public ConcurrentLinkedDeque<E> pollAdd() {
		
		state = State.ADDLOG;
		ConcurrentLinkedDeque<E> addC = add;
		add.clear();

	}

	public ConcurrentLinkedDeque<E> pollRemove() {

	}

}
