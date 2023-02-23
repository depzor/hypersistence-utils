package io.hypersistence.utils.spring.repository;

import javax.persistence.EntityManager;
import javax.persistence.LockModeType;

import io.hypersistence.utils.hibernate.util.ReflectionUtils;
import org.hibernate.Session;
import org.hibernate.engine.jdbc.spi.JdbcServices;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.internal.AbstractSharedSessionContract;
import org.springframework.data.jpa.repository.support.JpaEntityInformation;
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * @author Vlad Mihalcea
 */
public class BaseJpaRepositoryImpl<T, ID> extends SimpleJpaRepository<T, ID>
    implements BaseJpaRepository<T, ID> {

    private static final String ID_MUST_NOT_BE_NULL = ReflectionUtils.getFieldValueOrNull(SimpleJpaRepository.class, "ID_MUST_NOT_BE_NULL");

    private final EntityManager entityManager;
    private final JpaEntityInformation entityInformation;

    public BaseJpaRepositoryImpl(JpaEntityInformation<T, ?> entityInformation, EntityManager entityManager) {
        super(entityInformation, entityManager);
        this.entityInformation = entityInformation;
        this.entityManager = entityManager;
    }

    @Transactional
    public <S extends T> S persist(S entity) {
        entityManager.persist(entity);
        return entity;
    }

    @Transactional
    public <S extends T> S persistAndFlush(S entity) {
        persist(entity);
        entityManager.flush();
        return entity;
    }

    @Transactional
    public <S extends T> List<S> persistAll(Iterable<S> entities) {
        List<S> result = new ArrayList<>();
        for(S entity : entities) {
            result.add(persist(entity));
        }
        return result;
    }

    @Transactional
    public <S extends T> List<S> peristAllAndFlush(Iterable<S> entities) {
        return executeBatch(() -> {
            List<S> result = new ArrayList<>();
            for(S entity : entities) {
                result.add(persist(entity));
            }
            entityManager.flush();
            return result;
        });
    }
    
    @Transactional
    public <S extends T> S merge(S entity) {
        return entityManager.merge(entity);
    }

    
    @Override
    @Transactional
    public <S extends T> S mergeAndFlush(S entity) {
        S result = merge(entity);
        entityManager.flush();
        return result;
    }

    @Override
    @Transactional
    public <S extends T> List<S> mergeAll(Iterable<S> entities) {
        List<S> result = new ArrayList<>();
        for(S entity : entities) {
            result.add(merge(entity));
        }
        return result;
    }

    @Override
    @Transactional
    public <S extends T> List<S> mergeAllAndFlush(Iterable<S> entities) {
        return executeBatch(() -> {
            List<S> result = new ArrayList<>();
            for(S entity : entities) {
                result.add(merge(entity));
            }
            entityManager.flush();
            return result;
        });
    }

    @Transactional
    public <S extends T> S update(S entity) {
        session().update(entity);
        return entity;
    }

    @Override
    @Transactional
    public <S extends T> S updateAndFlush(S entity) {
        update(entity);
        entityManager.flush();
        return entity;
    }

    @Override
    @Transactional
    public <S extends T> List<S> updateAll(Iterable<S> entities) {
        List<S> result = new ArrayList<>();
        for(S entity : entities) {
            result.add(update(entity));
        }
        return result;
    }

    @Override
    @Transactional
    public <S extends T> List<S> updateAllAndFlush(Iterable<S> entities) {
        return executeBatch(() -> {
            List<S> result = new ArrayList<>();
            for(S entity : entities) {
                result.add(update(entity));
            }
            entityManager.flush();
            return result;
        });
    }

    @Override
    public T getReferenceById(ID id) {
        Assert.notNull(id, ID_MUST_NOT_BE_NULL);
        return entityManager.getReference(getDomainClass(), id);
    }

    @Override
    public T lockById(ID id, LockModeType lockMode) {
        return (T) entityManager.find(entityInformation.getJavaType(), id, lockMode);
    }

    protected Integer getBatchSize(Session session) {
        SessionFactoryImplementor sessionFactory = session.getSessionFactory().unwrap(SessionFactoryImplementor.class);
        final JdbcServices jdbcServices = sessionFactory.getServiceRegistry().getService(JdbcServices.class);
        if(!jdbcServices.getExtractedMetaDataSupport().supportsBatchUpdates()) {
            return Integer.MIN_VALUE;
        }
        return session.unwrap(AbstractSharedSessionContract.class).getConfiguredJdbcBatchSize();
    }

    protected <R> R executeBatch(Supplier<R> callback) {
        Session session = session();
        Integer jdbcBatchSize = getBatchSize(session);
        Integer originalSessionBatchSize = session.getJdbcBatchSize();
        try {
            if (jdbcBatchSize == null) {
                session.setJdbcBatchSize(10);
            }
            return callback.get();
        } finally {
            session.setJdbcBatchSize(originalSessionBatchSize);
        }
    }

    protected Session session() {
        return entityManager.unwrap(Session.class);
    }
}
