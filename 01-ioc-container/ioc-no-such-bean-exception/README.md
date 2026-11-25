# NoSuchBeanDefinitionException & NoUniqueBeanDefinitionException

These are the two exceptions Spring throws when it can't settle on **exactly one bean** for a lookup:

| Exception | Meaning |
|---|---|
| `NoSuchBeanDefinitionException` | **Zero** beans match the requested name or type. |
| `NoUniqueBeanDefinitionException` | **More than one** bean matches the requested type, and Spring can't pick one. |

`NoUniqueBeanDefinitionException` **extends** `NoSuchBeanDefinitionException`:

```
BeansException
 └── NoSuchBeanDefinitionException
      └── NoUniqueBeanDefinitionException
```

So a `catch (NoSuchBeanDefinitionException e)` block catches both, and so does
`assertThrows(NoSuchBeanDefinitionException.class, ...)`. Use `assertThrowsExactly` in tests
when you need to tell them apart (see [AppTest](src/test/java/dev/shermuhammad/ioc/AppTest.java)).

## Module layout

| File | Purpose |
|---|---|
| [MyBean](src/main/java/dev/shermuhammad/ioc/beans/MyBean.java) | A plain class used as the bean type. |
| [NoSuchBeanConfig](src/main/java/dev/shermuhammad/ioc/config/NoSuchBeanConfig.java) | An empty `@Configuration`: no `MyBean` is registered. |
| [NoUniqueBeanConfig](src/main/java/dev/shermuhammad/ioc/config/NoUniqueBeanConfig.java) | Registers two `MyBean` beans: `getGenericBean` and `getSpecialBean`. |
| [AppTest](src/test/java/dev/shermuhammad/ioc/AppTest.java) | Reproduces each exception and asserts on its details. |

Run the tests from the repository root:

```bash
./gradlew :01-ioc-container:ioc-no-such-bean-exception:test
```

---

## 1. NoSuchBeanDefinitionException

### How it happens

```java
try (var context = new AnnotationConfigApplicationContext(NoSuchBeanConfig.class)) {
    context.getBean(MyBean.class);   // nothing of type MyBean is registered
}
```

```
NoSuchBeanDefinitionException: No qualifying bean of type 'dev.shermuhammad.ioc.beans.MyBean' available
```

Calling `context.getBean("missing")` with a name that isn't registered throws the same exception.

### Common real-world causes

- **The class is never registered.** It's missing `@Component`/`@Service`/... or a `@Bean` method,
  or its `@Configuration` class was never passed to the context or imported.
- **Component scanning misses the package.** `@ComponentScan` only scans its own package and
  sub-packages by default.
- **A condition is not met.** `@Profile("dev")` with no active `dev` profile, or a
  `@Conditional...` that evaluates to false, means the bean is never registered.
- **The name is wrong.** A typo, or a mistaken assumption about the default name. `@Bean` methods
  are named after the method (`getGenericBean`). Components are named after the class with a
  lowercase first letter (`myBean`).
- **You asked for the implementation class, but the bean is a proxy.** With JDK interface-based
  proxies (for example `@Transactional` or `@Async` in plain Spring), the bean only implements
  the interface. Looking it up by the concrete class finds nothing. Look it up by the interface,
  or enable class-based proxies (`proxyTargetClass = true`).

### During injection

When the missing bean is a **dependency** (constructor, field or setter), the context fails at
startup. The error is wrapped like this:

```
UnsatisfiedDependencyException: Error creating bean with name 'service' ...
  Caused by: NoSuchBeanDefinitionException: No qualifying bean of type 'MyBean' available:
  expected at least 1 bean which qualifies as autowire candidate.
```

Always read down to the **root cause**. It names the missing type and the injection point.

### Mitigation

Fix the registration first: add the annotation, the `@Bean` method or the scan package, or
activate the profile. If the dependency is genuinely **optional**, say so explicitly:

```java
// 1. Optional<T>: empty when there's no bean
MyService(Optional<MyBean> bean) { ... }

// 2. ObjectProvider<T>: lazy and flexible
MyService(ObjectProvider<MyBean> provider) {
    MyBean bean = provider.getIfAvailable();                // null when there's none
    MyBean orDefault = provider.getIfAvailable(MyBean::new); // fallback supplier
}

// 3. Not required (field/setter injection)
@Autowired(required = false)
private MyBean bean;

// 4. A collection: empty when there's none
MyService(List<MyBean> beans) { ... }
```

For programmatic lookups, check before you fetch:

```java
context.containsBean("getGenericBean");        // by name
context.getBeanNamesForType(MyBean.class);      // String[]: empty if none
context.getBeanProvider(MyBean.class).getIfAvailable();
```

---

## 2. NoUniqueBeanDefinitionException

### How it happens

```java
try (var context = new AnnotationConfigApplicationContext(NoUniqueBeanConfig.class)) {
    context.getBean(MyBean.class);   // two candidates: getGenericBean, getSpecialBean
}
```

```
NoUniqueBeanDefinitionException: No qualifying bean of type 'dev.shermuhammad.ioc.beans.MyBean'
available: expected single matching bean but found 2: getGenericBean,getSpecialBean
```

The exception tells you what clashed:

```java
ex.getNumberOfBeansFound();   // 2
ex.getBeanNamesFound();       // [getGenericBean, getSpecialBean]
ex.getBeanType();             // MyBean.class
```

### Common real-world causes

- Two `@Bean` methods return the same type, as in this module.
- Two implementations of the same interface are both components (`SmsNotifier`, `EmailNotifier`
  implementing `Notifier`), and something injects plain `Notifier`.
- A library or auto-configuration registers a bean of a type you also define yourself
  (`ObjectMapper`, `DataSource`, ...).

### Mitigation

Pick the option that states *why* one bean should win:

**a) `@Primary`: one default for everyone**

```java
@Bean @Primary
public MyBean genericBean() { return new MyBean(); }
```

`getBean(MyBean.class)` and plain injection now return `genericBean`. Other beans can still be
requested explicitly.

**b) `@Fallback` (Spring 6.2+): the reverse of `@Primary`**

Marks a bean to be used only when no other candidate exists.

**c) `@Qualifier`: the caller chooses**

```java
@Bean @Qualifier("special")
public MyBean specialBean() { return new MyBean(); }

MyService(@Qualifier("special") MyBean bean) { ... }
```

**d) Look up by name**

```java
context.getBean("getSpecialBean", MyBean.class);
```

**e) Match by parameter or field name**

When there are several candidates and no `@Primary`, Spring tries to match the injection point's
name against bean names:

```java
MyService(MyBean specialBean) { ... }   // picks the bean named "specialBean"
```

For constructor and method **parameters**, this needs the `-parameters` compiler flag. Since
Spring 6.1, Spring no longer reads parameter names from debug info, and Gradle doesn't add the
flag by default:

```kotlin
tasks.withType<JavaCompile> { options.compilerArgs.add("-parameters") }
```

This is fragile, because renaming a variable changes the wiring. Prefer `@Qualifier`.

**f) Take them all**

```java
MyService(List<MyBean> beans) { ... }          // ordered by @Order / Ordered
MyService(Map<String, MyBean> beansByName) { ... }
context.getBeansOfType(MyBean.class);           // Map<String, MyBean>
```

This is useful for strategy and plugin patterns, where having several beans is the point.

**g) Tolerate ambiguity**

```java
provider.getIfUnique();   // null when there are zero OR several candidates
```

**h) Remove the duplicate**

Sometimes the second bean is a mistake. Delete it, or exclude it from scanning or
auto-configuration.

---

## Debugging tips

- **List what's registered:**

  ```java
  Arrays.stream(context.getBeanDefinitionNames()).sorted().forEach(System.out::println);
  ```

- **Check a type:** `context.getBeanNamesForType(MyBean.class)` returns 0 names for "no such"
  and 2 or more for "not unique".
- **Read the whole cause chain.** At startup, the useful message is usually the last `Caused by:`.
- **Watch the bean names.** `@Bean` method names become bean names, so prefer nouns
  (`genericBean()`) over getters (`getGenericBean()`), or set the name with `@Bean("genericBean")`.
- **Fail fast on purpose.** Prefer constructor injection with required dependencies, so wiring
  problems show up when the context starts, not later at the first `getBean` call.

## Related exceptions

| Exception | When |
|---|---|
| `UnsatisfiedDependencyException` | Injection failed. Often wraps one of the two exceptions above. |
| `BeanNotOfRequiredTypeException` | A bean with that **name** exists, but it's not the requested type (`getBean("x", Foo.class)`), often because of a proxy. |
| `BeanCurrentlyInCreationException` | A circular dependency between beans. |
| `BeanCreationException` | The bean was found, but its constructor or factory method threw. |
