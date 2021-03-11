package savvas;


//NOT RELEVANT
//NOT RELEVANT
//NOT RELEVANT
//NOT RELEVANT
//NOT RELEVANT
//NOT RELEVANT
//NOT RELEVANT


import java.util.concurrent.ConcurrentLinkedDeque;

public class ConcurrentUniqueElementDeque<E> {

	private ConcurrentLinkedDeque<E> que = new ConcurrentLinkedDeque<E>();

	public ConcurrentUniqueElementDeque() {

	}

	public boolean add(E elm) {
		if (!que.contains(elm)) {
			que.add(elm);
			return true;
		}
		return false;
	}

	public boolean remove(E elm) {
		que.remove(elm);
		if (!que.contains(elm)) 
			return true;
		return remove(elm);
	}
	
	

}
