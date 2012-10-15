/**
 * Copyright Alex Objelean
 */
package ro.isdc.wro.model.group.processor;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ro.isdc.wro.cache.CacheEntry;
import ro.isdc.wro.cache.CacheStrategy;
import ro.isdc.wro.cache.ContentHashEntry;
import ro.isdc.wro.cache.DefaultSynchronizedCacheStrategyDecorator;
import ro.isdc.wro.config.Context;
import ro.isdc.wro.config.ReadOnlyContext;
import ro.isdc.wro.config.jmx.WroConfiguration;
import ro.isdc.wro.manager.WroManager;
import ro.isdc.wro.manager.callback.LifecycleCallbackRegistry;
import ro.isdc.wro.manager.factory.WroManagerFactory;
import ro.isdc.wro.model.factory.DefaultWroModelFactoryDecorator;
import ro.isdc.wro.model.factory.WroModelFactory;
import ro.isdc.wro.model.group.GroupExtractor;
import ro.isdc.wro.model.resource.locator.factory.InjectorAwareUriLocatorFactoryDecorator;
import ro.isdc.wro.model.resource.locator.factory.UriLocatorFactory;
import ro.isdc.wro.model.resource.processor.factory.ProcessorsFactory;
import ro.isdc.wro.model.resource.support.ResourceAuthorizationManager;
import ro.isdc.wro.model.resource.support.hash.HashStrategy;
import ro.isdc.wro.model.resource.support.naming.NamingStrategy;
import ro.isdc.wro.util.LazyInitializer;
import ro.isdc.wro.util.ObjectFactory;
import ro.isdc.wro.util.ProxyFactory;


/**
 * Responsible for building the {@link Injector}. It can build an {@link Injector} without needing a {@link WroManager},
 * but just by providing required dependencies.
 *
 * @author Alex Objelean
 * @since 1.4.3
 * @created 6 Jan 2012
 */
public class InjectorBuilder {
  private static final Logger LOG = LoggerFactory.getLogger(InjectorBuilder.class);
  private final GroupsProcessor groupsProcessor = new GroupsProcessor();
  private final PreProcessorExecutor preProcessorExecutor = new PreProcessorExecutor();
  /**
   * A list of model transformers. Allows manager to mutate the model before it is being parsed and processed.
   */
  private Injector injector;
  /**
   * Mapping of classes to be annotated and the corresponding injected object. TODO: probably replace this map with
   * something like spring ApplicationContext (lightweight IoC).
   */
  private final Map<Class<?>, Object> map = new HashMap<Class<?>, Object>();
  private WroManagerFactory managerFactory;
  private final LazyInitializer<UriLocatorFactory> locatorFactoryInitializer = new LazyInitializer<UriLocatorFactory>() {
    @Override
    protected UriLocatorFactory initialize() {
      final WroManager manager = managerFactory.create();
      // update manager with new decorated factory
      manager.setUriLocatorFactory(InjectorAwareUriLocatorFactoryDecorator.decorate(manager.getUriLocatorFactory(),
          injector));
      return manager.getUriLocatorFactory();
    }
  };
  private final LazyInitializer<ResourceAuthorizationManager> authorizationManagerInitializer = new LazyInitializer<ResourceAuthorizationManager>() {
    @Override
    protected ResourceAuthorizationManager initialize() {
      final WroManager manager = managerFactory.create();
      return manager.getResourceAuthorizationManager();
    }
  };

  private final LazyInitializer<WroModelFactory> modelFactoryInitializer = new LazyInitializer<WroModelFactory>() {
    @Override
    protected WroModelFactory initialize() {
      final WroManager manager = managerFactory.create();
      // update manager with new decorated factory
      manager.setModelFactory(DefaultWroModelFactoryDecorator.decorate(manager.getModelFactory(), manager
          .getModelTransformers()));
      return manager.getModelFactory();
    }
  };
  /**
   * Ensure the strategy is decorated only once.
   */
  private final LazyInitializer<CacheStrategy<CacheEntry, ContentHashEntry>> cacheStrategyInitializer = new LazyInitializer<CacheStrategy<CacheEntry, ContentHashEntry>>() {
    @Override
    protected CacheStrategy<CacheEntry, ContentHashEntry> initialize() {
      final WroManager manager = managerFactory.create();
      // update manager with new decorated strategy
      manager.setCacheStrategy(DefaultSynchronizedCacheStrategyDecorator.decorate(manager.getCacheStrategy()));
      return manager.getCacheStrategy();
    }
  };

  /**
   * Use factory method {@link InjectorBuilder#create(WroManagerFactory)} instead.
   *
   * @VisibleForTesting
   */
  public InjectorBuilder() {
  }

  /**
   * Factory method which uses a managerFactory to initialize injected fields.
   */
  public static InjectorBuilder create(final WroManagerFactory managerFactory) {
    Validate.notNull(managerFactory);
    return new InjectorBuilder(managerFactory);
  }

  public InjectorBuilder(final WroManagerFactory managerFactory) {
    Validate.notNull(managerFactory);
    this.managerFactory = managerFactory;
  }

  private void initMap() {
    map.put(PreProcessorExecutor.class, createPreProcessorExecutorProxy());
    map.put(GroupsProcessor.class, createGroupsProcessorProxy());
    map.put(LifecycleCallbackRegistry.class, createCallbackRegistryProxy());
    map.put(GroupExtractor.class, createGroupExtractorProxy());
    map.put(Injector.class, createInjectorProxy());
    map.put(UriLocatorFactory.class, createLocatorFactoryProxy());
    map.put(ProcessorsFactory.class, createProcessorFactoryProxy());
    map.put(WroModelFactory.class, createModelFactoryProxy());
    map.put(NamingStrategy.class, createNamingStrategyProxy());
    map.put(HashStrategy.class, createHashStrategyProxy());
    map.put(ReadOnlyContext.class, createReadOnlyContextProxy());
    map.put(WroConfiguration.class, createConfigProxy());
    map.put(CacheStrategy.class, createCacheStrategyProxy());
    map.put(ResourceAuthorizationManager.class, createResourceAuthorizationManagerProxy());
  }

  private InjectorObjectFactory<WroConfiguration> createConfigProxy() {
    return new InjectorObjectFactory<WroConfiguration>() {
      public WroConfiguration create() {
        LOG.warn("Do not @Inject WroConfiguration. Prefer using @Inject ReadOnlyContext context; (and context.getConfig()).");
        return Context.get().getConfig();
      }
    };
  }

  private InjectorObjectFactory<PreProcessorExecutor> createPreProcessorExecutorProxy() {
    return new InjectorObjectFactory<PreProcessorExecutor>() {
      public PreProcessorExecutor create() {
        injector.inject(preProcessorExecutor);
        return preProcessorExecutor;
      }
    };
  }

  private InjectorObjectFactory<GroupsProcessor> createGroupsProcessorProxy() {
    return new InjectorObjectFactory<GroupsProcessor>() {
      public GroupsProcessor create() {
        injector.inject(groupsProcessor);
        return groupsProcessor;
      }
    };
  }

  private InjectorObjectFactory<LifecycleCallbackRegistry> createCallbackRegistryProxy() {
    return new InjectorObjectFactory<LifecycleCallbackRegistry>() {
      public LifecycleCallbackRegistry create() {
        final LifecycleCallbackRegistry callbackRegistry = managerFactory.create().getCallbackRegistry();
        injector.inject(callbackRegistry);
        return callbackRegistry;
      }
    };
  }

  private InjectorObjectFactory<Injector> createInjectorProxy() {
    return new InjectorObjectFactory<Injector>() {
      public Injector create() {
        return injector;
      }
    };
  }

  private Object createGroupExtractorProxy() {
    return new InjectorObjectFactory<GroupExtractor>() {
      public GroupExtractor create() {
        final GroupExtractor groupExtractor = managerFactory.create().getGroupExtractor();
        injector.inject(groupExtractor);
        final GroupExtractor proxy = new ProxyFactory<GroupExtractor>(groupExtractor, GroupExtractor.class).create();
        return proxy;
      }
    };
  }

  private Object createProcessorFactoryProxy() {
    return new InjectorObjectFactory<ProcessorsFactory>() {
      public ProcessorsFactory create() {
        final ProcessorsFactory proxy = new ProxyFactory<ProcessorsFactory>(
            managerFactory.create().getProcessorsFactory(), ProcessorsFactory.class).create();
        return proxy;
      }
    };
  }

  private Object createLocatorFactoryProxy() {
    return new InjectorObjectFactory<UriLocatorFactory>() {
      public UriLocatorFactory create() {
        final UriLocatorFactory proxy = new ProxyFactory<UriLocatorFactory>(locatorFactoryInitializer.get(),
            UriLocatorFactory.class).create();
        return proxy;
      }
    };
  }

  private Object createResourceAuthorizationManagerProxy() {
    return new InjectorObjectFactory<ResourceAuthorizationManager>() {
      public ResourceAuthorizationManager create() {
        final ResourceAuthorizationManager proxy = new ProxyFactory<ResourceAuthorizationManager>(
            authorizationManagerInitializer.get(), ResourceAuthorizationManager.class).create();
        return proxy;
      }
    };
  }

  private Object createModelFactoryProxy() {
    return new InjectorObjectFactory<WroModelFactory>() {
      public WroModelFactory create() {
        final WroModelFactory modelFactory = modelFactoryInitializer.get();
        injector.inject(modelFactory);
        final WroModelFactory proxy = new ProxyFactory<WroModelFactory>(modelFactory, WroModelFactory.class).create();
        return proxy;
      }
    };
  }

  private Object createNamingStrategyProxy() {
    return new InjectorObjectFactory<NamingStrategy>() {
      public NamingStrategy create() {
        final NamingStrategy namingStrategy = managerFactory.create().getNamingStrategy();
        injector.inject(namingStrategy);
        final NamingStrategy proxy = new ProxyFactory<NamingStrategy>(namingStrategy, NamingStrategy.class).create();
        return proxy;
      }
    };
  }

  private Object createHashStrategyProxy() {
    return new InjectorObjectFactory<HashStrategy>() {
      public HashStrategy create() {
        final HashStrategy hashStrategy = managerFactory.create().getHashStrategy();
        injector.inject(hashStrategy);
        final HashStrategy proxy = new ProxyFactory<HashStrategy>(hashStrategy, HashStrategy.class).create();
        return proxy;
      }
    };
  }

  @SuppressWarnings("rawtypes")
  private Object createCacheStrategyProxy() {
    return new InjectorObjectFactory<CacheStrategy>() {
      public CacheStrategy create() {
        final CacheStrategy<CacheEntry, ContentHashEntry> decorated = cacheStrategyInitializer.get();
        injector.inject(decorated);
        final CacheStrategy<?, ?> proxy = new ProxyFactory<CacheStrategy>(decorated, CacheStrategy.class).create();
        return proxy;
      }
    };
  }

  /**
   * @return a proxy of {@link ReadOnlyContext} object. This solution is preferred to {@link InjectorObjectFactory}
   *         because the injected field ensure thread-safe behavior.
   */
  private Object createReadOnlyContextProxy() {
    return new InjectorObjectFactory<ReadOnlyContext>() {
      public ReadOnlyContext create() {
        final ReadOnlyContext proxy = new ProxyFactory<ReadOnlyContext>(Context.get(), ReadOnlyContext.class).create();
        return proxy;
      }
    };
  }

  public Injector build() {
    // first initialize the map
    initMap();
    return injector = new Injector(Collections.unmodifiableMap(map));
  }

  /**
   * A special type used for lazy object injection only in context of this class.
   */
  static interface InjectorObjectFactory<T>
      extends ObjectFactory<T> {
  };
}
