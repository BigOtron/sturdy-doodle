# BeanFactory Demo: a bare `DefaultListableBeanFactory`, step by step

This module drives Spring's core IoC engine **by hand**, with no `ApplicationContext`, no annotations
and no component scanning. Every step that a context normally hides (registering definitions,
adding post-processors, creating beans) is an explicit line of code, so you can watch exactly
what the container does and in what order.

It shows:

- that a `BeanFactory` is **lazy**: registering a bean creates nothing;
- how a **constructor dependency** (`Car` → `Engine`) is resolved;
- **aliases**, the **singleton cache**, and lookup **by name vs by type**;
- how a `BeanPostProcessor` sees every bean, including a **`FactoryBean` and its product**;
- the **`&` prefix**, which returns the `FactoryBean` itself instead of the object it makes.

Versions: Spring Framework **7.1.0** (`spring-context`, set in the `java-conventions` plugin in
`buildSrc`), Java 17 toolchain.

## Module layout

| File | Purpose |
|---|---|
| [BeanFactoryDemo](src/main/java/dev/shermuhammad/ioc/BeanFactoryDemo.java) | `main`: builds the factory, registers beans, looks them up and prints what happens. |
| [Engine](src/main/java/dev/shermuhammad/ioc/beans/Engine.java) | A bean with no dependencies. It prints when constructed. |
| [Car](src/main/java/dev/shermuhammad/ioc/beans/Car.java) | A bean that needs an `Engine` through its **constructor**. |
| [ClockFactoryBean](src/main/java/dev/shermuhammad/ioc/beans/ClockFactoryBean.java) | A `FactoryBean<Clock>`: a bean whose job is to produce another object. |
| [SimpleBeanPostProcessor](src/main/java/dev/shermuhammad/ioc/bpp/SimpleBeanPostProcessor.java) | Logs every object passed to `postProcessAfterInitialization`. |

Run it from the repository root:

```bash
./gradlew :01-ioc-container:bean-factory-demo:run
```

## Output

This is real output from running the code on Spring Framework 7.1.0:

```
registered, nothing created yet. car singleton? true
Engine created
  BPP after-init: engine (Engine)
Car created
  BPP after-init: car (Car)
same instance by type? true
  BPP after-init: clock (ClockFactoryBean)
  BPP after-init: clock (SystemClock)
clock  -> SystemClock
&clock -> ClockFactoryBean
```

The rest of this README explains every line.

---

## 1. What a `BeanFactory` is

`BeanFactory` is the **root contract of the Spring IoC container**: give it a bean name or type and
it gives you back a fully configured object. Everything else is layered on top of it in separate,
narrow interfaces:

| Need | Interface |
|---|---|
| Look up one bean (`getBean`, `containsBean`, `isSingleton`, `getType`) | `BeanFactory` |
| List all beans, find beans by type or annotation | `ListableBeanFactory` |
| Have a parent container | `HierarchicalBeanFactory` |
| Register bean definitions | `BeanDefinitionRegistry` |
| Add post-processors, scopes, converters | `ConfigurableBeanFactory` |
| Create and wire objects Spring doesn't manage | `AutowireCapableBeanFactory` |

`DefaultListableBeanFactory` implements **all** of them. It is the real engine: every
`ApplicationContext` creates one internally and delegates bean work to it. Its superclass chain
splits the work into layers:

```txt
SimpleAliasRegistry                         alias → name map
 └ DefaultSingletonBeanRegistry             singleton cache, destroy callbacks
   └ FactoryBeanRegistrySupport             cache of FactoryBean products
     └ AbstractBeanFactory                  getBean()/doGetBean(), merged definitions, scopes, BPP list
       └ AbstractAutowireCapableBeanFactory createBean(): instantiate → populate → initialize
         └ DefaultListableBeanFactory       bean definition map, by-type lookups, preInstantiateSingletons()
```

### Bare factory vs `ApplicationContext`

| | Bare `DefaultListableBeanFactory` (this demo) | `ApplicationContext` |
|---|---|---|
| When singletons are created | **Lazily**, on the first `getBean` | Eagerly, during `refresh()` |
| `BeanPostProcessor`s | You call `addBeanPostProcessor` yourself | Found among the beans, sorted and registered automatically |
| `@Autowired`, `@PostConstruct`, `@Configuration` | **Ignored** (their processors aren't installed) | Work (`AnnotationConfigApplicationContext` registers the processors) |
| Events, messages, resources, `Environment` | None | Included |

Applications should use an `ApplicationContext`. A bare factory is useful for tests, framework
code, and learning, as here.

---

## 2. The beans

### `Engine` and `Car`

```java
public class Car {
    private final Engine engine;

    public Car(Engine engine) {
        this.engine = engine;
        System.out.println("Car created");
    }
}
```

`Car` has a **single constructor that takes an `Engine`**. Spring can't call it until an `Engine`
exists, so `Engine` will always be created first (see section 6).

Neither class has a Spring annotation. Spring doesn't need any: the bean definitions in
`BeanFactoryDemo` tell it which classes to manage and how to wire them.

### `ClockFactoryBean`

```java
public class ClockFactoryBean implements FactoryBean<Clock> {
    public Clock getObject()          { return Clock.systemUTC(); }
    public Class<?> getObjectType()   { return Clock.class; }
}
```

A `FactoryBean<T>` is a bean that **produces** another object. The container registers and
manages the `ClockFactoryBean`, but `getBean("clock")` returns the **product** (`getObject()`),
not the factory. `isSingleton()` isn't overridden, and its default is `true`, so the product is
created once and cached.

Use it when building an object takes real logic. You use FactoryBeans all the time without
noticing: every Spring Data repository is the product of a `JpaRepositoryFactoryBean`, and
`LocalContainerEntityManagerFactoryBean` and MyBatis's `SqlSessionFactoryBean` are FactoryBeans too.

> Don't confuse `BeanFactory` (the container) with `FactoryBean` (a bean that makes objects).

### `SimpleBeanPostProcessor`

```java
public Object postProcessAfterInitialization(Object bean, String beanName) {
    System.out.println("  BPP after-init: " + beanName + " (" + bean.getClass().getSimpleName() + ")");
    return bean;
}
```

A `BeanPostProcessor` (BPP) is a hook that runs for **every bean the factory creates**: once before
initialization and once after. Only the after-init callback is overridden here; the before-init one
is a `default` method that returns the bean unchanged.

Whatever the BPP **returns** is what the container stores and hands out. Returning the same
`bean` changes nothing; returning a wrapper would replace it. That is exactly how Spring AOP
creates proxies for `@Transactional` and `@Async` beans.

---

## 3. Creating the factory

```java
var bf = new DefaultListableBeanFactory();
```

You get the engine with nothing installed. These are the defaults (checked on 7.1.0):

| Setting | Default | Note |
|---|---|---|
| Bean definitions | none | |
| `BeanPostProcessor`s | none | So no annotation processing |
| `allowBeanDefinitionOverriding` | `true` | Spring Boot sets it to `false` |
| `allowCircularReferences` | `true` | Spring Boot 2.6+ sets it to `false` |
| Instantiation strategy | `CglibSubclassingInstantiationStrategy` | |
| Autowire candidate resolver | `SimpleAutowireCandidateResolver` | Doesn't understand `@Qualifier`, `@Lazy` or `@Value`. A context replaces it. |

---

## 4. Registering bean definitions

```java
bf.registerBeanDefinition("engine",
        BeanDefinitionBuilder.genericBeanDefinition(Engine.class).getBeanDefinition());
bf.registerBeanDefinition("car",
        BeanDefinitionBuilder.genericBeanDefinition(Car.class)
                .addConstructorArgReference("engine").getBeanDefinition());
bf.registerBeanDefinition("clock",
        BeanDefinitionBuilder.genericBeanDefinition(ClockFactoryBean.class).getBeanDefinition());
```

A **`BeanDefinition`** is a *recipe*, not an object: which class to create, its scope, constructor
arguments, properties, init and destroy methods, and whether it's lazy. Registering one creates
**nothing**.

- `genericBeanDefinition(Engine.class)` builds a `GenericBeanDefinition` with the bean class set. The
  scope is left empty, which means **singleton**.
- `addConstructorArgReference("engine")` stores a `RuntimeBeanReference("engine")` as constructor
  argument 0. It's a **placeholder holding a bean name**. It's replaced by the real `Engine` only
  when `car` is created.
- `registerBeanDefinition` validates the definition, checks whether the name is already taken
  (overriding), and stores it in the factory's `beanDefinitionMap`.

Because references are resolved late, registration order doesn't matter: `car` could be registered
before `engine`.

This is the same thing every configuration style does in the end. `XmlBeanDefinitionReader`,
component scanning and `@Configuration`/`@Bean` processing all turn their input into
`BeanDefinition`s and call `registerBeanDefinition`.

---

## 5. Alias

```java
bf.registerAlias("car", "vehicle");
```

`"vehicle"` becomes another name for `"car"`. Aliases are stored in an alias → canonical name map.
On every lookup the name is first resolved to its canonical form, so `getBean("vehicle")` and
`getBean("car")` return the same object. `bf.getAliases("car")` returns `[vehicle]`.

The equivalents in other styles are `@Bean({"car", "vehicle"})` (the first name is canonical) and,
in XML, `name="car,vehicle"` or `<alias>`.

---

## 6. Asking about a bean without creating it

```java
System.out.println("registered, nothing created yet. car singleton? " + bf.isSingleton("car"));
```

```
registered, nothing created yet. car singleton? true
```

`isSingleton` answers from the **bean definition** (the recipe), so `car` is not created.
Nothing has been printed by a constructor yet: this is what "a `BeanFactory` is lazy" means.

> **Edge case:** for a `FactoryBean`, `isSingleton("clock")` **does** create the `ClockFactoryBean`,
> because Spring has to call `factoryBean.isSingleton()` and needs an instance for that.
> `getType("clock")` returns `Clock` **without** creating anything: Spring reads the type from the
> `FactoryBean<Clock>` generic signature. Both behaviours were checked on 7.1.0.

---

## 7. Adding the `BeanPostProcessor`

```java
bf.addBeanPostProcessor(new SimpleBeanPostProcessor());
```

A bare factory doesn't look for post-processors among its beans. You add them yourself.

- They are applied **in the order you add them**. `Ordered`, `PriorityOrdered` and `@Order` are only
  honoured when an `ApplicationContext` registers BPPs for you.
- A BPP only affects beans created **after** it's added. If `engine` were fetched before this line,
  the BPP would never log it, even on later lookups (also checked on 7.1.0). That's why an
  `ApplicationContext` registers all BPPs before creating ordinary singletons.

---

## 8. `getBean("vehicle", Car.class)`: creating `Car` and `Engine`

```java
Car car = bf.getBean("vehicle", Car.class);
```

```
Engine created
  BPP after-init: engine (Engine)
Car created
  BPP after-init: car (Car)
```

What happens inside (`AbstractBeanFactory.doGetBean`):

```txt
doGetBean("vehicle")
 ├ resolve the alias → "car"
 ├ singleton cache → miss
 ├ merge the bean definition → RootBeanDefinition
 └ create the singleton (marks "car" as currently in creation)
    └ doCreateBean
       ├ 1. instantiate: there are constructor args, so use autowireConstructor
       │     └ resolve RuntimeBeanReference("engine") → getBean("engine")
       │          └ the whole cycle for engine → "Engine created" → BPP: engine
       │     → new Car(engine) → "Car created"
       ├ 2. populate properties (none here)
       └ 3. initialize → BPP: car
 → check the result is a Car → put it in the singleton cache → return it
```

**Why `Engine created` comes first:** with **constructor injection** the dependency must be
**completely finished**, including its post-processors, before the constructor can be called.
With setter or field injection the order would flip: `Car created` first, and `Engine` created
while `Car`'s properties are populated.

This is also why Spring **can't resolve a circular dependency between constructors**. There is no
half-built `Car` it could hand to `Engine` early.

**The `Car.class` argument is a type check, not a cast.** If the bean isn't of that type, Spring
first tries to convert it, then fails:

```
getBean("vehicle", Clock.class)
→ BeanNotOfRequiredTypeException: Bean named 'vehicle' is expected to be of type 'java.time.Clock'
  but was actually of type '...Car'
```

---

## 9. `getBean(Car.class)`: lookup by type and the singleton cache

```java
System.out.println("same instance by type? " + (car == bf.getBean(Car.class)));
```

```
same instance by type? true
```

Looking up by **type** takes a different path from looking up by name. `getBeanNamesForType(Car.class)`
checks **every** bean definition and predicts its type. Exactly one name matches (`car`), so it
calls `getBean("car")`, which is now a **cache hit**: no constructor runs and the BPP isn't
called again. Singleton scope means one instance per container, per bean name.

- With **two** `Car` definitions this throws
  `NoUniqueBeanDefinitionException: ... expected single matching bean but found 2: car,car2`
  (see the `ioc-no-such-bean-exception` module). Ways to break the tie include `primary` and `@Priority`.
- In a bare factory, by-type lookups are **not cached**. The type → names cache only works after
  `freezeConfiguration()`, which an `ApplicationContext` calls during `refresh()`. So calling
  `getBean(SomeType.class)` in a hot loop on a bare factory is slow.

---

## 10. `getBean("clock")`: a `FactoryBean` and its product

```java
System.out.println("clock  -> " + bf.getBean("clock").getClass().getSimpleName());
```

```
  BPP after-init: clock (ClockFactoryBean)
  BPP after-init: clock (SystemClock)
clock  -> SystemClock
```

The BPP runs **twice** with the name `clock`, once for each of two different objects:

```txt
doGetBean("clock") → cache miss → create ClockFactoryBean (full lifecycle)
                                    → BPP: clock (ClockFactoryBean)        ← 1st line
it's a FactoryBean and the name has no "&" → get its product:
   isSingleton() is true → product cache miss
   → getObject() → Clock.systemUTC()
   → postProcessObjectFromFactoryBean → BPP: clock (SystemClock)          ← 2nd line
   → cache the product
```

1. The `ClockFactoryBean` is an ordinary bean, so it goes through the **full** lifecycle.
2. The product gets **only** `postProcessAfterInitialization`: no injection, no Aware callbacks,
   no `@PostConstruct`, no before-init. Spring didn't construct it, so it can't run the rest of the
   lifecycle on it. After-init is enough for AOP, though, which is why `@Transactional` works on
   Spring Data repositories.

The product is cached, so a second `getBean("clock")` prints nothing new.

`SystemClock` is the JDK's package-private class behind `Clock.systemUTC()`.

---

## 11. `getBean("&clock")`: the factory itself

```java
System.out.println("&clock -> " + bf.getBean("&clock").getClass().getSimpleName());
```

```
&clock -> ClockFactoryBean
```

The `&` prefix (`BeanFactory.FACTORY_BEAN_PREFIX`) means "give me the `FactoryBean`, not its
product". Spring strips the `&`, finds the already-created `ClockFactoryBean` in the singleton
cache, and returns it without calling `getObject()`. No BPP line is printed because nothing new is
created.

If the bean isn't a `FactoryBean`, you get an error, but only **after** the bean is created:
`getBean("&car")` creates `Engine` and `Car` and then throws
`BeanIsNotAFactoryException: Bean named 'car' is expected to be of type 'org.springframework.beans.factory.FactoryBean'...`.

---

## 12. Eager creation: `preInstantiateSingletons()`

The demo never calls it, but it explains how an `ApplicationContext` differs. Calling
`bf.preInstantiateSingletons()` after adding the BPP creates every non-lazy singleton up front.
Checked on 7.1.0:

```
  BPP after-init: clock (ClockFactoryBean)
Engine created
  BPP after-init: engine (Engine)
Car created
  BPP after-init: car (Car)
```

The `ClockFactoryBean` is created, but its **product isn't**. `getObject()` runs on the first
`getBean("clock")`, unless the factory implements `SmartFactoryBean` with `isEagerInit()` returning `true`.

---

## Things to try

1. **Setter injection.** Give `Car` a no-arg constructor and a `setEngine` method, and replace
   `addConstructorArgReference("engine")` with `addPropertyReference("engine", "engine")`. Predict the
   new output order before running it.
2. **Prototype product.** Override `isSingleton()` in `ClockFactoryBean` to return `false`, call
   `getBean("clock")` three times, and count the BPP lines.
3. **Late BPP.** Call `bf.getBean("engine")` before `addBeanPostProcessor`. Which line disappears, and why?
4. **Wrapping BPP.** Make the BPP return a different object for `engine` (for example a subclass of
   `Engine`). Which object does `Car` receive?
5. **Annotations do nothing.** Add an `@Autowired` field to `Car` and check that it stays `null`. Then
   make it work by adding `AutowiredAnnotationBeanPostProcessor` (call `setBeanFactory(bf)` on it first).

## References

- [The IoC Container: Container Overview](https://docs.spring.io/spring-framework/reference/core/beans/basics.html)
- [The BeanFactory API](https://docs.spring.io/spring-framework/reference/core/beans/beanfactory.html)
- [Customizing Instantiation Logic with a FactoryBean](https://docs.spring.io/spring-framework/reference/core/beans/factory-extension.html#beans-factory-extension-factorybean)
- [DefaultListableBeanFactory Javadoc](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/beans/factory/support/DefaultListableBeanFactory.html)
