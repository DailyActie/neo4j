package org.neo4j.impl.core;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import org.neo4j.impl.event.Event;
import org.neo4j.impl.event.EventData;
import org.neo4j.impl.event.EventManager;
import org.neo4j.impl.persistence.IdGenerator;
import org.neo4j.impl.persistence.PersistenceException;
import org.neo4j.impl.persistence.PersistenceManager;
import org.neo4j.impl.transaction.TransactionFactory;
import org.neo4j.impl.util.ArrayMap;

// TODO: make LRU x elements
public class PropertyIndex
{
	private static ArrayMap<String,List<PropertyIndex>> indexMap
		= new ArrayMap<String,List<PropertyIndex>>( 5, true, false );
	private static ArrayMap<Integer,PropertyIndex> idToIndexMap
		= new ArrayMap<Integer,PropertyIndex>( 9, true, false );
	
	static void clear()
	{
		indexMap = new ArrayMap<String,List<PropertyIndex>>( 5, true, false );
		idToIndexMap = new ArrayMap<Integer,PropertyIndex>( 9, true, false );
	}
	
	public static Iterable<PropertyIndex> index( String key )
	{
//		if ( key == null )
//		{
//			throw new IllegalArgumentException( "null key" );
//		}
		List<PropertyIndex> list = indexMap.get( key );
		if ( list != null )
		{
			return list;
		}
		return Collections.EMPTY_LIST;
	}
	
	static boolean hasAll()
	{
		return true;
	}
	
	public static PropertyIndex createDummyIndex( int id, String key )
	{
		return new PropertyIndex( key, id );
	}
	
	public static boolean hasIndexFor( int keyId )
	{
		return idToIndexMap.get( keyId ) != null;
	}
	
	public static PropertyIndex getIndexFor( int keyId )
	{
		// TODO: add loading
		PropertyIndex index = idToIndexMap.get( keyId );
		if ( index == null )
		{
			String indexString;
            try
            {
	            indexString = PersistenceManager.getManager().loadIndex( 
	            	keyId );
				if ( indexString == null )
				{
					throw new NotFoundException( "Index not found [" + keyId + 
						"]" );
				}
				index = new PropertyIndex( indexString, keyId );
				addPropertyIndex( index );
            }
            catch ( PersistenceException e )
            {
	            // TODO Auto-generated catch block
	            e.printStackTrace();
            }
		}
		return index;
	}
	
	static void addPropertyIndex( PropertyIndex index )
	{
		List<PropertyIndex> list = indexMap.get( index.getKey() );
		if ( list == null )
		{
			list = new LinkedList<PropertyIndex>();
			indexMap.put( index.getKey(), list );
		}
		// indexMap.put( index.getKey(), index );
		list.add( index );
		idToIndexMap.put( index.getKeyId(), index );
	}
	
	static void removePropertyIndex( PropertyIndex index )
	{
		List<PropertyIndex> list = indexMap.get( index.getKey() );
		if ( list != null )
		{
			Iterator<PropertyIndex> itr = list.iterator();
			while ( itr.hasNext() )
			{
				PropertyIndex element = itr.next();
				if ( element.getKeyId() == index.getKeyId() )
				{
					itr.remove();
					break;
				}
			}
			if ( list.size() == 0 )
			{
				indexMap.remove( index.getKey() );
			}
		}
		idToIndexMap.remove( index.getKeyId() );
	}
	
	// concurent threads may create duplicate keys, oh well
	static PropertyIndex createPropertyIndex( String key )
	{
		int id = IdGenerator.getGenerator().nextId( PropertyIndex.class );
		PropertyIndex index = new PropertyIndex( key, id );
//		PropertyIndexCommands propertyIndexCommand = null;
//		try
//		{
//			propertyIndexCommand = new PropertyIndexCommands();
//			propertyIndexCommand.setPropertyIndex( index );
//			propertyIndexCommand.initCreate();

			EventManager em = EventManager.getManager();
			EventData eventData = new EventData( new PropIndexOpData( index ) );
			if ( !em.generateProActiveEvent( Event.PROPERTY_INDEX_CREATE, 
				eventData ) )
			{
				setRollbackOnly();
				throw new CreateException( "Unable to create property index, " +
					"pro-active event failed." );
			}
//			propertyIndexCommand.execute();
			addPropertyIndex( index );
			em.generateReActiveEvent( Event.PROPERTY_INDEX_CREATE, eventData );
			return index;
//		}
//		catch ( ExecuteFailedException e )
//		{
//			setRollbackOnly();
//			if ( propertyIndexCommand != null )
//			{
//				propertyIndexCommand.undo();
//			}
//			throw new CreateException( "Failed executing command", e );
//		}
	}
	
	private final String key;
	private final int keyId;
	
	PropertyIndex( String key, int keyId )
	{
		this.key = key;
		this.keyId = keyId;
	}
	
	public String getKey()
	{
		return key;
	}
	
//	public char[] getChars()
//	{
//		if ( chars == null )
//		{
//			int keyLength = key.length();
//			chars = new char[ keyLength ];
//			key.getChars( 0, keyLength, chars, 0 );
//		}
//		return chars;
//	}
	
	@Override
	public int hashCode()
	{
		return keyId;
	}
	
	public int getKeyId()
	{
		return this.keyId;
	}
	
	@Override
	public boolean equals( Object o )
	{
		if ( o instanceof PropertyIndex )
		{
			return keyId == ((PropertyIndex ) o).getKeyId();
		}
		return false;
	}

	private static void setRollbackOnly()
	{
		try
		{
			TransactionFactory.getTransactionManager().setRollbackOnly();
		}
		catch ( javax.transaction.SystemException se )
		{
			se.printStackTrace();
		}
	}

	public static void removeIndex( int id )
    {
		PropertyIndex index = idToIndexMap.remove( id );
		if ( index != null )
		{
			removePropertyIndex( index );
		}
    }
	
	static class PropIndexOpData implements PropertyIndexOperationEventData
	{
		private final PropertyIndex index;
		
		PropIndexOpData( PropertyIndex index )
		{
			this.index = index;
		}
		
		public PropertyIndex getIndex()
        {
			return index;
        }

		public Object getEntity()
        {
			return index;
        }
	}
}
