package com.sudoplay.ecs.core;

import com.sudoplay.ecs.integration.api.Entity;
import com.sudoplay.ecs.integration.api.EntitySet;
import com.sudoplay.ecs.integration.spi.EntitySetEventHandler;
import net.openhft.koloboke.collect.map.hash.HashLongObjMaps;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.function.Consumer;

public class EntitySetInternal implements
    EntitySet {

  /* package */ enum EventType {
    ADD, CHANGE, REMOVE
  }

  private Map<Long, BitSet> entityComponentBitSetMap;
  private Aspect aspect;
  private Map<Long, Entity> entityMap;
  private List<Reference<EntitySetEventHandler>> eventHandlerList;

  /* package */ EntitySetInternal(
      Map<Long, BitSet> entityComponentBitSetMap,
      Aspect aspect
  ) {

    this.entityComponentBitSetMap = entityComponentBitSetMap;
    this.aspect = aspect;

    this.entityMap = HashLongObjMaps
        .getDefaultFactory()
        .newUpdatableMap();

    this.eventHandlerList = new ArrayList<>();
  }

  /* package */ void onSystemEvent(
      EntityInternal entity,
      EventType eventType
  ) {

    long entityId;
    BitSet componentBitSet;
    boolean interested;
    boolean contains;

    entityId = entity.getId();
    componentBitSet = this.entityComponentBitSetMap.get(entityId);
    interested = this.aspect.matches(componentBitSet);
    contains = this.entityMap.containsKey(entityId);

    if (eventType == EventType.ADD
        || eventType == EventType.CHANGE) {

      if (interested && !contains) {

        // we like this entity and we don't have it - we covets
        // this covers the case where an entity is added and
        // this view is interested in it

        this.entityMap.put(entityId, entity);

        // notify our subscribers and do some reference bookkeeping

        this.subscriberNotifyEntityAdded(entity);

      } else if (!interested && contains) {

        // we don't care about this entity anymore - discard it
        // this covers the case where an entity has changed and
        // this view is no longer interested in the entity

        this.entityMap.remove(entityId);

        // notify our subscribers and do some reference bookkeeping

        this.subscriberNotifyEntityRemoved(entity);

      } else if (contains) {

        // this covers the case where this view is interested in
        // the entity, but it already contains the entity

        // currently do nothing
      }

      // if this view is not interested in and doesn't contain
      // the entity, don't do anything

    } else if (eventType == EventType.REMOVE) {

      if (contains) {

        // this view has the entity being removed and needs to
        // remove it

        this.entityMap.remove(entityId);

        // notify our subscribers and do some reference bookkeeping

        this.subscriberNotifyEntityRemoved(entity);

      }

    }

  }

  @Override
  public boolean contains(Entity entity) {

    return this.entityMap.containsKey(((EntityInternal) entity).getId());
  }

  @Override
  public void forEach(Consumer<Entity> consumer) {

    this.entityMap.values()
        .forEach(consumer);
  }

  @Override
  public void subscribe(EntitySetEventHandler eventHandler) {

    this.eventHandlerList.add(new WeakReference<>(eventHandler));
  }

  private void subscriberNotifyEntityAdded(EntityInternal entity) {

    for (Iterator<Reference<EntitySetEventHandler>> iterator = this.eventHandlerList.iterator(); iterator.hasNext(); ) {

      Reference<EntitySetEventHandler> ref = iterator.next();

      EntitySetEventHandler eventHandler = ref.get();

      if (eventHandler == null) {
        iterator.remove();
        ref.clear();
        continue;
      }

      eventHandler.onEventEntityAdded(entity);

    }
  }

  private void subscriberNotifyEntityRemoved(EntityInternal entity) {

    for (Iterator<Reference<EntitySetEventHandler>> iterator = this.eventHandlerList.iterator(); iterator.hasNext(); ) {

      Reference<EntitySetEventHandler> ref = iterator.next();

      EntitySetEventHandler eventHandler = ref.get();

      if (eventHandler == null) {
        iterator.remove();
        ref.clear();
        continue;
      }

      eventHandler.onEventEntityRemoved(entity);

    }
  }

}