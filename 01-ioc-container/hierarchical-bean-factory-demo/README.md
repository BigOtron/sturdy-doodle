# HierarchicalBeanFactory Demo: parent and child bean factories

A `HierarchicalBeanFactory` is a bean factory that can have a **parent**. When the child doesn't
know a bean, it asks the parent. This module wires two bare `DefaultListableBeanFactory` instances
into a parent → child pair by hand and prints what each lookup method returns.

It shows:

- how a child **delegates** `getBean(name)` to its parent, and how a local bean **shadows** a parent bean with the same name;
- `containsBean` (looks at ancestors) vs `containsLocalBean` (this factory only);
- that the parent **cannot see** the child;
- that `ListableBeanFactory` methods such as `getBeanNamesForType` **ignore the parent**, and how `BeanFactoryUtils` fills the gap;
- the trap: `getBean(Class)` **does** fall back to the parent, even though the by-type name listing returns `[]`;
- that a bean resolved through the child is the **parent's own singleton**;
- that a factory's parent can be set **only once**.

Versions: Spring Framework **7.1.0** (`spring-context`, set in the `java-conventions` plugin in
`buildSrc`), Java 17 toolchain.

## Module layout

| File | Purpose |
|---|---|
| [App](src/main/java/dev/shermuhammad/ioc/App.java) | `main`: builds the parent and child factories, then prints what each lookup returns. |
| [Greeter](src/main/java/dev/shermuhammad/ioc/beans/Greeter.java) | Holds a `text`. Defined in **both** factories under the name `greeter`, to show shadowing. |
| [Repo](src/main/java/dev/shermuhammad/ioc/beans/Repo.java) | Holds an `owner`. Defined **only in the parent** under the name `repo`, to show delegation. |

Run it from the repository root:

```bash
./gradlew :01-ioc-container:hierarchical-bean-factory-demo:run
```

## Output

This is real output from running the code on Spring Framework 7.1.0:

```
parent of child = true
child greeter    = Hello, from CHILD greeter
child repo       = PARENT
containsBean(repo)      = true
containsLocalBean(repo) = false
parent greeter   = Hello, from PARENT greeter
child.getBeanNamesForType(Repo) = []
including ancestors             = [repo]
child.getBean(Repo.class)       = PARENT
same instance    = true
setParent again  -> Already associated with parent BeanFactory
```

The rest of this README explains every line.

---

## 1. What `HierarchicalBeanFactory` adds

The interface is tiny. It adds two methods to `BeanFactory`:

```java
public interface HierarchicalBeanFactory extends BeanFactory {
    @Nullable BeanFactory getParentBeanFactory();
    boolean containsLocalBean(String name);   // ignores ancestors
}
```

It has **no setter**. The parent is set by bootstrap code, through `ConfigurableBeanFactory`:

```txt
BeanFactory
 └ HierarchicalBeanFactory          getParentBeanFactory, containsLocalBean
    └ ConfigurableBeanFactory       + setParentBeanFactory (and scopes, BPPs, ...)
       └ AbstractBeanFactory        implements the parent delegation (doGetBean)
          └ ... DefaultListableBeanFactory
```

This is the usual Spring split: **read-only interfaces for clients**, `Configurable*` interfaces for
the code that builds the container. Every `ApplicationContext` is a `HierarchicalBeanFactory` too,
and `ConfigurableApplicationContext.setParent(..)` builds context hierarchies on top of this
mechanism.

There are two ways to give a factory a parent:

```java
new DefaultListableBeanFactory(parent);     // constructor, used in App
child.setParentBeanFactory(parent);         // ConfigurableBeanFactory setter
```

### Why hierarchies exist

A hierarchy gives you **shared beans plus isolated children**. One parent holds the expensive,
common infrastructure: the `DataSource`, services, caches. Each child adds its own beans on top. A
child sees everything in the parent, while the parent and the sibling children never see that
child's beans. That gives you:

- **sharing without duplication:** parent singletons are created once and reused by every child (section 9);
- **isolation between siblings:** two children can each define a bean with the same name without any clash;
- **local override:** a child shadows a parent bean by name, without touching the parent (section 3);
- **separate lifecycles:** a child can be closed or refreshed while the parent keeps running;
- **a one-way dependency rule:** parent code can never depend on child beans.

Real uses: the Spring MVC **root context** (services) with one **child context per
`DispatcherServlet`** (controllers); Spring Cloud's `NamedContextFactory` (one child context per
Feign or LoadBalancer client); Spring Boot Actuator on a separate `management.server.port` (a child
context); and Boot's `SpringApplicationBuilder.parent(..).child(..)`. A typical Spring Boot app,
though, runs **one flat context**. See the pitfalls at the end.

---

## 2. Building the hierarchy

```java
DefaultListableBeanFactory parent = new DefaultListableBeanFactory();
parent.registerBeanDefinition("greeter",
        new RootBeanDefinition(Greeter.class, () -> new Greeter("Hello, from PARENT greeter")));
parent.registerBeanDefinition("repo",
        new RootBeanDefinition(Repo.class, () -> new Repo("PARENT")));

DefaultListableBeanFactory child = new DefaultListableBeanFactory(parent);
child.registerBeanDefinition("greeter",
        new RootBeanDefinition(Greeter.class, () -> new Greeter("Hello, from CHILD greeter")));

HierarchicalBeanFactory h = child;
System.out.println("parent of child = " + (h.getParentBeanFactory() == parent));
```

```
parent of child = true
```

The `RootBeanDefinition(Class, Supplier)` constructor gives each definition an **instance
supplier**. Spring calls the lambda instead of a constructor, so `Greeter` and `Repo` need no
no-arg constructor.

After this code, the two factories hold:

| Bean name | Parent | Child |
|---|---|---|
| `greeter` | `Greeter("…PARENT…")` | `Greeter("…CHILD…")` |
| `repo` | `Repo("PARENT")` | — |

These are two **separate registries**. The child's `greeter` is not an override of the parent's
definition in the `allowBeanDefinitionOverriding` sense. It lives in a different map, so Spring
logs nothing and throws nothing.

---

## 3. `getBean(name)`: local first, then the parent

```java
System.out.println("child greeter    = " + child.getBean("greeter", Greeter.class).getText());
System.out.println("child repo       = " + child.getBean("repo", Repo.class).getOwner());
```

```
child greeter    = Hello, from CHILD greeter
child repo       = PARENT
```

All lookup by name goes through `AbstractBeanFactory.doGetBean`. Simplified from the 7.1.0 source:

```txt
doGetBean(name):
    beanName = transformedBeanName(name)        // strip "&", resolve aliases
    1. instance = getSingleton(beanName)        // local singleton cache
       found? → return it
    2. parent = getParentBeanFactory()
       if parent != null && !containsBeanDefinition(beanName):
           return parent.getBean(originalBeanName(name), ...)   // delegate
    3. create the bean from the local definition
```

- `greeter`: the child has its own definition, so step 2 is skipped and the **child's** bean is
  created. The parent's `greeter` is **shadowed**.
- `repo`: the child has no definition, so the call is **delegated** to the parent.

Details that matter:

- **Step 1 comes first.** A singleton registered with `registerSingleton` in the child shadows a
  parent definition too, even though the child has no definition for it. (Checked on 7.1.0.)
- `originalBeanName(name)` **keeps the `&` prefix**, so `child.getBean("&myFactoryBean")` returns
  the parent's `FactoryBean` itself, not its product.
- Delegation is **recursive**: if the parent doesn't have the bean, it asks its own parent. If no
  factory in the chain has it, you get
  `NoSuchBeanDefinitionException: No bean named 'x' available`.

---

## 4. `containsBean` vs `containsLocalBean`

```java
System.out.println("containsBean(repo)      = " + child.containsBean("repo"));
System.out.println("containsLocalBean(repo) = " + child.containsLocalBean("repo"));
```

```
containsBean(repo)      = true
containsLocalBean(repo) = false
```

| Method | Declared on | Checks |
|---|---|---|
| `containsBean(name)` | `BeanFactory` | Local singletons or definitions, **then the parent chain**. |
| `containsLocalBean(name)` | `HierarchicalBeanFactory` | Local singletons or definitions **only**. Never asks the parent. |
| `containsBeanDefinition(name)` | `ListableBeanFactory` | Local **definitions only** (not manual singletons, not the parent). |

`containsBean` follows the same rule as `getBean`: it answers **"can I get this bean from
here?"**. `containsLocalBean` answers **"does this factory itself own it?"**. You use it when you
need to know whether a bean is shadowed, and `BeanFactoryUtils` uses it for exactly that (section 7).

The other `BeanFactory` query methods delegate exactly like `getBean`, with the same
`!containsBeanDefinition(beanName)` check: `isSingleton`, `isPrototype`, `isTypeMatch` and
`getType`. `getAliases` merges the local aliases with the parent's.

---

## 5. The parent cannot see the child

```java
System.out.println("parent greeter   = " + parent.getBean("greeter", Greeter.class).getText());
```

```
parent greeter   = Hello, from PARENT greeter
```

A factory holds a reference to its parent, but the parent holds **no reference** to its children.
So the parent always returns its own `greeter`, and a bean that exists only in the child is simply
unknown to the parent: `parent.containsBean(...)` is `false` and `parent.getBean(...)` throws
`NoSuchBeanDefinitionException`. (Checked on 7.1.0.)

This is the mechanism behind the one-way dependency rule in section 1: a service in the root
context can't inject a controller from the servlet context.

---

## 6. `ListableBeanFactory` methods ignore the parent

```java
System.out.println("child.getBeanNamesForType(Repo) = "
        + Arrays.toString(child.getBeanNamesForType(Repo.class)));
```

```
child.getBeanNamesForType(Repo) = []
```

The `ListableBeanFactory` Javadoc says it directly: if the factory is also a
`HierarchicalBeanFactory`, the enumeration methods **take no notice of beans in ancestor
factories** and consider only this factory's own definitions (plus manual singletons for the
by-type methods; see the listable demo).

The same is true of `getBeansOfType`, `getBeanDefinitionNames`, `getBeanDefinitionCount`,
`getBeanNamesForAnnotation` and `getBeansWithAnnotation`. In this demo the child has exactly
one definition (`greeter`), so `child.getBeanDefinitionCount()` is `1`.

**Why this design?** Enumeration methods describe **what this factory contains**, and that is what
the container needs internally, for example to pre-instantiate its own singletons or to find its own
`BeanPostProcessor`s. Including the parent would make the child create, process or count beans
that belong to another container. Walking the hierarchy is left to an explicit utility.

---

## 7. `BeanFactoryUtils`: walking the ancestors

```java
System.out.println("including ancestors             = "
        + Arrays.toString(BeanFactoryUtils.beanNamesForTypeIncludingAncestors(child, Repo.class)));
```

```
including ancestors             = [repo]
```

`BeanFactoryUtils` has an `...IncludingAncestors` variant for each enumeration method:

| Local only (`ListableBeanFactory`) | Including ancestors (`BeanFactoryUtils`) |
|---|---|
| `getBeanNamesForType` | `beanNamesForTypeIncludingAncestors` |
| `getBeansOfType` | `beansOfTypeIncludingAncestors` |
| `getBeanNamesForAnnotation` | `beanNamesForAnnotationIncludingAncestors` |
| `getBeansWithAnnotation` | `beansWithAnnotationIncludingAncestors` |
| — | `countBeansIncludingAncestors`, `beanNamesIncludingAncestors` |
| — | `beanOfTypeIncludingAncestors` (exactly one match, or an exception) |

How `beanNamesForTypeIncludingAncestors` works (7.1.0 source, simplified):

```txt
result = lbf.getBeanNamesForType(type)                // local names first
if lbf is Hierarchical and its parent is Listable:
    parentResult = beanNamesForTypeIncludingAncestors(parent, type)   // recursive
    for name in parentResult:
        if name not in result && !hbf.containsLocalBean(name):
            result.add(name)                          // parent names last, shadowed names skipped
```

So the result is **unique by name**. Child names come first, then ancestor names that the child
does **not** shadow. The `containsLocalBean` check is important: a parent `repo` is skipped if the
child owns **any** bean called `repo`, even a bean of a different type.

Checked on 7.1.0:

- child `childRepo` + parent `repo` → `[childRepo, repo]`;
- child `repo` + parent `repo` → `[repo]`, and `beansOfTypeIncludingAncestors` returns the **child's** instance.

The parent must itself be a `ListableBeanFactory` for the walk to continue. Every Spring factory
and context is.

---

## 8. The trap: `getBean(Class)` does use the parent

```java
System.out.println("child.getBean(Repo.class)       = " + child.getBean(Repo.class).getOwner());
```

```
child.getBean(Repo.class)       = PARENT
```

Section 6 found **no** `Repo` names in the child, yet lookup by type works. `getBean(Class)` is a
`BeanFactory` method, so it follows the "can I get it from here?" rule. In
`DefaultListableBeanFactory.resolveBean` (7.1.0):

```txt
resolveBean(type):
    namedBean = resolveNamedBean(type)         // LOCAL candidates only
    found?            → return it
    parent is a DefaultListableBeanFactory → parent.resolveBean(type)   // recursive
    other parent      → parent.getBeanProvider(type).getIfAvailable()
    no parent         → null → NoSuchBeanDefinitionException
```

Consequences:

- **A local match wins outright.** If the child has its own `Repo` (under any name), the parent is
  never asked, so there's **no** `NoUniqueBeanDefinitionException` between child and parent beans.
  (Checked on 7.1.0: child `childRepo` + parent `repo` → `getBean(Repo.class)` returns the child's.)
- Uniqueness is checked **per level**: two `Repo`s in the child still fail with
  `NoUniqueBeanDefinitionException`.
- **Autowiring and `ObjectProvider` see ancestors too.** `findAutowireCandidates` uses
  `BeanFactoryUtils.beanNamesForTypeIncludingAncestors`, so a child bean can inject a parent bean.
  `getBeanProvider(Repo.class).stream()` returns child **and** parent beans. (Both checked on
  7.1.0: a constructor-autowired child bean got the parent's `Repo`, and the stream returned
  `[CHILD, PARENT]`.)

So the rule is: **`BeanFactory` methods, autowiring and `ObjectProvider` look at ancestors. The
`ListableBeanFactory` enumeration methods don't.**

---

## 9. The parent owns the instance

```java
System.out.println("same instance    = " + (child.getBean("repo") == parent.getBean("repo")));
```

```
same instance    = true
```

The child **delegates** and does **not copy**. The `Repo` singleton is created once and cached in
the **parent's** `singletonObjects`. The child's cache stays empty for it.

Everything about that bean belongs to the parent:

- its **scope** is resolved by the parent;
- the **parent's `BeanPostProcessor`s** process it. The child's BPPs never touch it;
- the **parent destroys it**. Checked on 7.1.0: after `child.destroySingletons()`, the parent's
  `repo` is still in the parent's cache.

---

## 10. A parent can be set only once

```java
try {
    child.setParentBeanFactory(new DefaultListableBeanFactory());
} catch (IllegalStateException e) {
    System.out.println("setParent again  -> " + e.getMessage().split(":")[0]);
}
```

```
setParent again  -> Already associated with parent BeanFactory
```

`AbstractBeanFactory.setParentBeanFactory` (7.1.0):

```java
if (this.parentBeanFactory != null && this.parentBeanFactory != parentBeanFactory) {
    throw new IllegalStateException("Already associated with parent BeanFactory: " + this.parentBeanFactory);
}
if (this == parentBeanFactory) {
    throw new IllegalStateException("Cannot set parent bean factory to self");
}
```

- Setting the **same** parent again is allowed (a no-op). Checked on 7.1.0.
- A **different** parent is rejected. Once beans may have been resolved through a parent, swapping
  it would leave the factory inconsistent.
- A factory **cannot be its own parent**. Longer cycles (A → B → A) are not checked here.

The full message also contains the old parent's `toString()`, which lists its bean names. That's why
`App` keeps only the part before the first `:`.

---

## Summary

| Method | Declared on | Looks at ancestors? |
|---|---|---|
| `getBean(name)` / `getBean(name, type)` | `BeanFactory` | ✅ if no local definition or singleton |
| `getBean(Class)` | `BeanFactory` | ✅ if no local match |
| `getBeanProvider(..)` | `BeanFactory` | ✅ (stream includes ancestors) |
| `containsBean`, `isSingleton`, `isPrototype`, `isTypeMatch`, `getType`, `getAliases` | `BeanFactory` | ✅ |
| `containsLocalBean` | `HierarchicalBeanFactory` | ❌ |
| `getParentBeanFactory` | `HierarchicalBeanFactory` | one level up |
| `containsBeanDefinition`, `getBeanDefinitionNames`, `getBeanDefinitionCount` | `ListableBeanFactory` | ❌ |
| `getBeanNamesForType`, `getBeansOfType`, `...ForAnnotation`, `...WithAnnotation` | `ListableBeanFactory` | ❌ |
| `BeanFactoryUtils.*IncludingAncestors` | utility | ✅ (unique names, child first) |
| Autowiring (`findAutowireCandidates`) | internal | ✅ |

## Pitfalls (beyond the docs)

- **`BeanPostProcessor`s don't cross factories.** If `@EnableTransactionManagement` or
  `@EnableAsync` is declared only in the child (for example the servlet context), beans in the
  parent are **not proxied**, and `@Transactional` on root services silently does nothing.
- **Overlapping component scans.** If the root and servlet contexts scan the same packages, every
  service exists **twice**. The child's copy shadows the parent's, so the two copies may get
  different proxies.
- **Shadowing is silent.** No warning or exception is raised when a child bean hides a parent bean
  with the same name.
- **Configuration is per factory.** Property placeholders, `BeanFactoryPostProcessor`s and scopes
  are set up separately in each context.
- **Enumeration surprises.** `ctx.getBeansOfType(...)` in a child context misses parent beans. Use
  `BeanFactoryUtils` or `ObjectProvider.stream()` when you need all levels.

## Things to try

1. **Three levels.** Build `grandparent → parent → child`. Put `config` only in the grandparent and
   shadow `service` in the parent. Check that `child.getBean("config")` resolves from the
   grandparent, `child.getBean("service")` returns the parent's version, and `containsLocalBean` is
   `false` for both on the child.
2. **Who wins by type?** Register a `Repo` in the child under a **different** name. Predict, then
   check, what `child.getBean(Repo.class)`, `getBeanNamesForType` and
   `beanNamesForTypeIncludingAncestors` return. Then repeat with the **same** name in both.
3. **Manual singleton shadow.** Replace the child's `greeter` definition with
   `child.registerSingleton("greeter", ...)`. Which `greeter` does `child.getBean("greeter")`
   return? Point to the step in section 3 that decides it.
4. **Your own `findAll`.** Implement
   `static <T> Map<String, T> beansOfTypeWithAncestors(ListableBeanFactory bf, Class<T> type)`
   **without** `BeanFactoryUtils`, letting child names shadow parent names. Check that it agrees
   with `BeanFactoryUtils.beansOfTypeIncludingAncestors` for the setups from task 2.
5. **Post-processor scope.** Add a `BeanPostProcessor` to the **child only**
   (`child.addBeanPostProcessor(...)`) that prints each bean name. Fetch `greeter` and `repo`
   through the child. Which names does it print, and why?

## References

- [The BeanFactory API](https://docs.spring.io/spring-framework/reference/core/beans/beanfactory.html)
- [HierarchicalBeanFactory Javadoc](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/beans/factory/HierarchicalBeanFactory.html)
- [ListableBeanFactory Javadoc](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/beans/factory/ListableBeanFactory.html)
- [BeanFactoryUtils Javadoc](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/beans/factory/BeanFactoryUtils.html)
- [ConfigurableBeanFactory Javadoc](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/beans/factory/config/ConfigurableBeanFactory.html)
- [Web MVC: Context Hierarchy](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-servlet/context-hierarchy.html)
