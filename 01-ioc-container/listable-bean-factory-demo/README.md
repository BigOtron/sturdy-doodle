# ListableBeanFactory Demo: enumerating beans, and what the factory can "see"

`BeanFactory` answers **"give me this one bean"**. `ListableBeanFactory` answers **"which beans are
there?"**. This module drives a bare `DefaultListableBeanFactory` by hand to show what the
enumeration methods return, which of them **create** beans and which only **read metadata**, and why
an object registered with `registerSingleton` is invisible to some methods and visible to others.

It shows:

- the **definition-only** methods: `getBeanDefinitionCount`, `getBeanDefinitionNames`, `containsBeanDefinition`;
- `containsBean` vs `containsBeanDefinition`;
- `getBeanNamesForType` (names only, **creates nothing**) vs `getBeansOfType` (**creates** every match);
- **`registerSingleton`**: a finished object with no `BeanDefinition`, tracked in `manualSingletonNames`;
- the order results come back in, and how the singleton cache fills up;
- **annotation lookup**: `getBeanNamesForAnnotation` (names only) vs `getBeansWithAnnotation` (creates the match).

Versions: Spring Framework **7.1.0** (`spring-context`, set in the `java-conventions` plugin in
`buildSrc`), Java 17 toolchain.

## Module layout

| File | Purpose |
|---|---|
| [App](src/main/java/dev/shermuhammad/ioc/App.java) | `main`: registers three definitions and one manual singleton, then prints what each enumeration method returns. |
| [Greeter](src/main/java/dev/shermuhammad/ioc/beans/Greeter.java) | The interface every bean implements, so they can all be found **by type**. |
| [EnglishGreeter](src/main/java/dev/shermuhammad/ioc/beans/EnglishGreeter.java) | Returns `"Hello"`. Registered as a bean definition. |
| [GermanGreeter](src/main/java/dev/shermuhammad/ioc/beans/GermanGreeter.java) | Returns `"Hallo"`. Registered as a bean definition. |
| [RussianGreeter](src/main/java/dev/shermuhammad/ioc/beans/RussianGreeter.java) | Returns `"Privet"`. Registered as a bean definition. |
| [MyCustomAnnotation](src/main/java/dev/shermuhammad/ioc/annotations/MyCustomAnnotation.java) | A marker annotation for classes (`@Target(TYPE)`, `@Retention(RUNTIME)`). |
| [BeanWithCustomAnnotation](src/main/java/dev/shermuhammad/ioc/beans/BeanWithCustomAnnotation.java) | A class annotated with `@MyCustomAnnotation`, found by annotation lookup. Not a `Greeter`. |

The fourth bean, `spanishGreeter`, is a lambda created in `App` and handed over with `registerSingleton`.

Run it from the repository root:

```bash
./gradlew :01-ioc-container:listable-bean-factory-demo:run
```

## Output

This is real output from running the code on Spring Framework 7.1.0:

```
bean definition count: 3
bean definition names: [englishGreeter, germanGreeter, russianGreeter]
hasDef(spanishGreeter) = false
containsBean(spanishGreeter) = true
namesForType = [englishGreeter, germanGreeter, russianGreeter, spanishGreeter]
singletonsSoFar = [spanishGreeter]
beansOfType = [englishGreeter, germanGreeter, russianGreeter, spanishGreeter]
singletonsNow   = [spanishGreeter, englishGreeter, germanGreeter, russianGreeter]
bean names annotated with custom annotation: [beanWithCustomAnnotation]
singletonsSoFar = [spanishGreeter, englishGreeter, germanGreeter, russianGreeter]
bean annotated with custom annotation: [beanWithCustomAnnotation]
singletonsSoFar = [spanishGreeter, englishGreeter, germanGreeter, russianGreeter, beanWithCustomAnnotation]
```

The rest of this README explains every line.

---

## 1. What `ListableBeanFactory` adds

`BeanFactory` can only look up a bean whose name or type you already know. `ListableBeanFactory`
extends it with methods that **list** beans:

```txt
BeanFactory                         getBean, containsBean, isSingleton, getType
 └ ListableBeanFactory              enumeration: names, by type, by annotation
    └ ConfigurableListableBeanFactory   + preInstantiateSingletons, freezeConfiguration, getBeanDefinition
       └ DefaultListableBeanFactory     the implementation (also a BeanDefinitionRegistry)
```

`ApplicationContext` extends `ListableBeanFactory` too. When you call `ctx.getBeansOfType(...)`,
the context just forwards the call to its internal `DefaultListableBeanFactory`.

The methods fall into groups by **what they cost**:

| Group | Methods | Creates beans? |
|---|---|---|
| Definition metadata | `getBeanDefinitionCount`, `getBeanDefinitionNames`, `containsBeanDefinition` | **No**. Reads the registry only. |
| Names by type | `getBeanNamesForType(...)` | Normally **no**. Predicts types from definitions. |
| Instances by type | `getBeansOfType(...)` → `Map<String, T>` | **Yes**. Calls `getBean` for every match. |
| Lazy access | `getBeanProvider(...)` → `ObjectProvider<T>` | Only when you use the provider. |
| Annotations | `getBeanNamesForAnnotation`, `getBeansWithAnnotation`, `findAnnotationOnBean` | Names: no. `getBeansWithAnnotation`: yes. |

This demo covers definition metadata, type lookup and annotation lookup. The type-lookup flags
(`includeNonSingletons`, `allowEagerInit`) and parent factories are separate topics.

### Two rules from the Javadoc

1. **Only this factory's bean definitions are considered.** Objects registered with
   `registerSingleton` are ignored, **except** by `getBeanNamesForType` and `getBeansOfType`, which
   check them as well. (Sections 4–6 show both sides of this rule.) In practice the annotation
   methods check them too (section 11).
2. **They are not meant for frequent calls.** Apart from `getBeanDefinitionCount` and
   `containsBeanDefinition`, these methods scan every definition. Call them once and keep the result.

---

## 2. Two registries inside `DefaultListableBeanFactory`

The factory stores beans in two different ways, and this is the key to every line of output:

| Field | Filled by | Holds |
|---|---|---|
| `beanDefinitionMap` + `beanDefinitionNames` (a list, in registration order) | `registerBeanDefinition` | **Recipes** (`BeanDefinition`s). Spring creates the object later. |
| `singletonObjects` (in `DefaultSingletonBeanRegistry`) | `getBean` (after creating), **and** `registerSingleton` | **Finished objects**: the singleton cache. |
| `manualSingletonNames` (a set) | `registerSingleton`, when no definition has that name | Names of singletons that have **no** definition. |

`singletonObjects` mixes two kinds of object: singletons Spring built from definitions, and objects
handed over by hand. `manualSingletonNames` is how the factory tells them apart, so that by-type
lookups can also find the hand-registered ones.

---

## 3. Registering the beans

```java
var bf = new DefaultListableBeanFactory();
bf.registerBeanDefinition("englishGreeter", new RootBeanDefinition(EnglishGreeter.class));
bf.registerBeanDefinition("germanGreeter", new RootBeanDefinition(GermanGreeter.class));
bf.registerBeanDefinition("russianGreeter", new RootBeanDefinition(RussianGreeter.class));

bf.registerSingleton("spanishGreeter", (Greeter) () -> "Hola");
```

- The three `registerBeanDefinition` calls store **recipes**. Nothing is instantiated. The names go
  into `beanDefinitionNames` in this exact order.
- `registerSingleton` stores a **finished object**. The `(Greeter)` cast gives the lambda a target
  type, so the object really is a `Greeter`.

### What `registerSingleton` does

It's declared on `SingletonBeanRegistry` and implemented in `DefaultSingletonBeanRegistry`.
`DefaultListableBeanFactory` overrides it to add the bookkeeping:

1. `super.registerSingleton(name, obj)` puts the object into `singletonObjects`. If the name is
   already taken, it throws:
   ```
   IllegalStateException: Could not register object [...] under bean name 'x': there is already object [...] bound
   ```
2. If **no** bean definition has that name, the name is added to `manualSingletonNames`.
3. The by-type cache is cleared, so later type lookups see the new object.

The object gets **no lifecycle at all**. The Javadoc says it must already be fully initialized:
Spring runs no `BeanPostProcessor`s, no init callbacks (`afterPropertiesSet`, init methods), no
injection, and **no destroy callbacks**. Checked on 7.1.0: after `bf.destroySingletons()`, a
manual singleton implementing `DisposableBean` was removed from the cache, but its `destroy()` was
never called. A bean created from a definition did get `destroy()`.

**Where Spring uses it:** an `ApplicationContext` registers `environment`, `systemProperties` and
`systemEnvironment` this way. Spring Boot does the same with `springApplicationArguments` and
`springBootBanner`. You'd use it for an object built outside Spring that should still be injectable.

> **Edge case (checked on 7.1.0):** if you later call `registerBeanDefinition` with the **same
> name** as a manual singleton, the name is removed from `manualSingletonNames`, the manual object
> is dropped from the cache, and the next `getBean` creates a new object from the definition.

---

## 4. Definition metadata: count and names

```java
System.out.println("bean definition count: " + bf.getBeanDefinitionCount());
System.out.println("bean definition names: " + Arrays.toString(bf.getBeanDefinitionNames()));
```

```
bean definition count: 3
bean definition names: [englishGreeter, germanGreeter, russianGreeter]
```

Both methods read only `beanDefinitionNames`. `spanishGreeter` has no definition, so it's **not
counted and not listed**, even though `getBean("spanishGreeter")` would work. Names come back in
**registration order**.

This is why, in an `ApplicationContext`, `getBeanDefinitionNames()` never contains `environment`:
it's a manual singleton.

---

## 5. `containsBeanDefinition` vs `containsBean`

```java
System.out.println("hasDef(spanishGreeter) = " + bf.containsBeanDefinition("spanishGreeter"));
System.out.println("containsBean(spanishGreeter) = " + bf.containsBean("spanishGreeter"));
```

```
hasDef(spanishGreeter) = false
containsBean(spanishGreeter) = true
```

| Method | Declared on | Checks |
|---|---|---|
| `containsBeanDefinition(name)` | `ListableBeanFactory` | Only the definition map. Doesn't resolve aliases or look at the parent. |
| `containsBean(name)` | `BeanFactory` | The singleton cache **or** the definition map, after resolving aliases, then the parent factory. |

So "does this factory know a bean called X?" is `containsBean`. "Is there a recipe for X?" is
`containsBeanDefinition`. Neither creates anything.

---

## 6. `getBeanNamesForType`: names only

```java
System.out.println("namesForType = " + Arrays.toString(bf.getBeanNamesForType(Greeter.class)));
```

```
namesForType = [englishGreeter, germanGreeter, russianGreeter, spanishGreeter]
```

This is the first exception to the "definitions only" rule: `spanishGreeter` **is** found. Inside
`DefaultListableBeanFactory.doGetBeanNamesForType`:

```txt
for each name in beanDefinitionNames (registration order):
    merged RootBeanDefinition → skip if abstract
    isTypeMatch(name, Greeter)
        instance already cached?  → check its real class
        otherwise                 → predict the type from the definition (bean class here)
for each name in manualSingletonNames:
    isTypeMatch(name, Greeter) → check the object's class
```

That's why the manual singleton comes **last**: definitions are scanned first, then manual
singletons.

**It creates nothing.** Type prediction reads the definition's bean class, so no constructor runs.
The next line proves it.

> In a bare factory this scan runs on every call. The type → names cache is only used after
> `freezeConfiguration()`, which an `ApplicationContext` calls during `refresh()`.

---

## 7. The singleton cache before `getBeansOfType`

```java
System.out.println("singletonsSoFar = " + Arrays.toString(bf.getSingletonNames()));
```

```
singletonsSoFar = [spanishGreeter]
```

`getSingletonNames()` (from `SingletonBeanRegistry`) lists the objects **currently in the singleton
cache**. Only the manual singleton is there. The three greeters are still just recipes.

This is **not** because they are lazy. Their definitions are ordinary eager singletons (`lazyInit`
defaults to `false`). A bare `DefaultListableBeanFactory` simply never creates beans on its own: it
creates one only when something calls `getBean` (directly, through `getBeansOfType`, through
autowiring, ...). The eager startup you know from Spring comes from `ApplicationContext.refresh()`,
which calls `beanFactory.preInstantiateSingletons()` at the end.

Checked on 7.1.0: adding `bf.preInstantiateSingletons();` right after the registrations changes this line to

```
singletonsSoFar = [spanishGreeter, englishGreeter, germanGreeter, russianGreeter]
```

---

## 8. `getBeansOfType`: creates every match

```java
System.out.println("beansOfType = " + bf.getBeansOfType(Greeter.class).keySet());
```

```
beansOfType = [englishGreeter, germanGreeter, russianGreeter, spanishGreeter]
```

`getBeansOfType` is `getBeanNamesForType` followed by `getBean(name)` for each name, collected into
a `LinkedHashMap`, so it keeps the same order as section 6.

- `englishGreeter`, `germanGreeter`, `russianGreeter`: cache miss, so each is **instantiated** and cached.
- `spanishGreeter`: cache hit. The object you registered is returned as is.

> **Pitfalls (beyond the docs):**
> - Calling `getBeansOfType` from a `BeanFactoryPostProcessor`, or while `BeanPostProcessor`s are
>   being registered, creates beans **too early**. They miss later BPPs (no proxies, no `@Autowired`),
>   and Spring logs *"Bean 'x' is not eligible for getting processed by all BeanPostProcessors"*.
>   Use `getBeanNamesForType` there instead.
> - The map is in **registration order** and ignores `@Order`/`Ordered`. Injecting `List<Greeter>` or
>   using `getBeanProvider(Greeter.class).orderedStream()` respects ordering.
> - In application code, prefer injecting `List<T>`, `Map<String, T>` or `ObjectProvider<T>` over
>   calling the factory directly.

---

## 9. The singleton cache after `getBeansOfType`

```java
System.out.println("singletonsNow   = " + Arrays.toString(bf.getSingletonNames()));
```

```
singletonsNow   = [spanishGreeter, englishGreeter, germanGreeter, russianGreeter]
```

Now all four objects are cached. The order is the order they **entered the cache**:
`spanishGreeter` was registered first, then the three greeters were created by `getBeansOfType`.
Compare with sections 6 and 8, where `spanishGreeter` came **last** because definitions are
scanned before manual singletons.

---

## 10. Registering a definition after beans were created

```java
bf.registerBeanDefinition("beanWithCustomAnnotation", new RootBeanDefinition(BeanWithCustomAnnotation.class));
```

The factory has already created four singletons, and you can still add a definition. A bare factory
never **freezes** its configuration, so the registry stays open. Registering clears the by-type cache
so later lookups see the new bean.

In an `ApplicationContext` you shouldn't do this after `refresh()`: the configuration is frozen,
singletons are already pre-instantiated, and the new definition misses steps like
`BeanFactoryPostProcessor`s. Registration happens before `refresh()` or in a
`BeanDefinitionRegistryPostProcessor`.

---

## 11. `getBeanNamesForAnnotation`: names only

```java
System.out.println("bean names annotated with custom annotation: " +
        Arrays.toString(bf.getBeanNamesForAnnotation(MyCustomAnnotation.class)));
System.out.println("singletonsSoFar = " + Arrays.toString(bf.getSingletonNames()));
```

```
bean names annotated with custom annotation: [beanWithCustomAnnotation]
singletonsSoFar = [spanishGreeter, englishGreeter, germanGreeter, russianGreeter]
```

Only `beanWithCustomAnnotation` carries the annotation. The cache line proves that the lookup **did
not create** the bean: `beanWithCustomAnnotation` is still missing from it.

How it works in `DefaultListableBeanFactory`:

```txt
for each name in beanDefinitionNames (skipping abstract definitions)
  + each name in manualSingletonNames:
    findAnnotationOnBean(name, MyCustomAnnotation)
        type = getType(name)                    // predicted from the definition, no instance needed
        search the class with MergedAnnotations (TYPE_HIERARCHY)
        for a @Bean bean: also check the factory method
```

Because it uses Spring's merged-annotation search rather than plain `Class.getAnnotation`, it also
finds the annotation:

- on a **superclass** or an **implemented interface**, even without `@Inherited`;
- as a **meta-annotation**: a bean annotated with `@Foo`, where `@Foo` itself is annotated with
  `@MyCustomAnnotation`. This is how `@Service` counts as `@Component`.

It also checks **manual singletons**, even though the `ListableBeanFactory` class Javadoc lists only
the two by-type methods as exceptions. Checked on 7.1.0: a `registerSingleton` object whose class
carries the annotation is returned, after the definitions, as in section 6.

> **Pitfalls (beyond the docs):**
> - **`@Retention(RUNTIME)` is required.** With `CLASS` (the default when you leave `@Retention` out)
>   the annotation isn't visible through reflection, and the lookup returns `[]`. Checked on 7.1.0.
> - The match is on the **bean's type**, so `getType` has to work without creating the bean. For a
>   `FactoryBean`, the Javadoc warns that the `FactoryBean` gets **initialized** to determine its
>   object type.
> - `@Target(TYPE)` means this annotation only marks classes. Annotations on methods or fields are
>   not what these methods look for.

---

## 12. `getBeansWithAnnotation`: creates the match

```java
System.out.println("bean annotated with custom annotation: " +
        bf.getBeansWithAnnotation(MyCustomAnnotation.class).keySet());
System.out.println("singletonsSoFar = " + Arrays.toString(bf.getSingletonNames()));
```

```
bean annotated with custom annotation: [beanWithCustomAnnotation]
singletonsSoFar = [spanishGreeter, englishGreeter, germanGreeter, russianGreeter, beanWithCustomAnnotation]
```

Like `getBeansOfType`, it is the name lookup followed by `getBean(name)` for each match, collected into
a `LinkedHashMap<String, Object>`. The bean is now **instantiated** and cached, so it appears at the
end of the singleton list.

The map's values are `Object` because the matches can be of any type. Use `findAnnotationOnBean(name,
type)` when you need the annotation's attributes without creating the bean.

The same early-creation pitfall as section 8 applies: don't call `getBeansWithAnnotation` from a
`BeanFactoryPostProcessor`.

---

## Summary

| Method | Sees definitions | Sees manual singletons | Creates beans |
|---|---|---|---|
| `getBeanDefinitionCount` / `getBeanDefinitionNames` | ✅ | ❌ | ❌ |
| `containsBeanDefinition` | ✅ | ❌ | ❌ |
| `containsBean` (`BeanFactory`) | ✅ | ✅ | ❌ |
| `getBeanNamesForType` | ✅ | ✅ (listed after definitions) | ❌ (normally) |
| `getBeansOfType` | ✅ | ✅ | ✅ |
| `getBeanNamesForAnnotation` | ✅ | ✅ (checked on 7.1.0) | ❌ (normally) |
| `getBeansWithAnnotation` | ✅ | ✅ | ✅ |
| `findAnnotationOnBean` | ✅ | ✅ | ❌ (normally) |
| `getSingletonNames` (`SingletonBeanRegistry`) | only once created | ✅ | ❌ |

## Things to try

1. **Lazy definition.** Register another `EnglishGreeter` definition named `lazyEnglish` with
   `setLazyInit(true)`. Does `getBeanNamesForType` return it? Does `getBeansOfType` instantiate it?
   Explain why each happens, then try the same after calling `preInstantiateSingletons()`.
2. **Duplicate manual singleton.** Call `registerSingleton("spanishGreeter", ...)` a second time.
   What happens, and why doesn't the same thing happen when you register a duplicate **definition**?
3. **Lifecycle check.** Make `RussianGreeter` implement `InitializingBean` and register a second
   instance of it with `registerSingleton`. Which one prints from `afterPropertiesSet()`?
4. **Definition after manual singleton.** Call `registerBeanDefinition("spanishGreeter", ...)` after
   the `registerSingleton` line. Predict the output of every line before running it.
5. **Meta-annotation.** Create `@SpecialGreeter`, annotated with `@MyCustomAnnotation`, and put it on
   `GermanGreeter`. Predict what `getBeanNamesForAnnotation(MyCustomAnnotation.class)` returns.
6. **Retention.** Remove `@Retention(RetentionPolicy.RUNTIME)` from `MyCustomAnnotation`. What do
   both annotation lines print now, and why?

## References

- [The BeanFactory API](https://docs.spring.io/spring-framework/reference/core/beans/beanfactory.html)
- [ListableBeanFactory Javadoc](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/beans/factory/ListableBeanFactory.html)
- [Annotations and meta-annotations (merged annotations)](https://docs.spring.io/spring-framework/reference/core/beans/classpath-scanning.html#beans-meta-annotations)
- [SingletonBeanRegistry Javadoc](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/beans/factory/config/SingletonBeanRegistry.html)
- [DefaultListableBeanFactory Javadoc](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/beans/factory/support/DefaultListableBeanFactory.html)
