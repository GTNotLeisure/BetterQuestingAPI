# BetterQuestingAPI

中文文档: [README.zh-CN.md](README.zh-CN.md)

BetterQuestingAPI is a Minecraft Forge 1.7.10 runtime API mod for registering and applying BetterQuesting chapters, quests, tasks, and rewards from code or imported resources.

## What This Project Provides

- Stable Java API for quest definition and registration.
- Runtime apply pipeline to inject registered definitions into BetterQuesting.
- Optional importer flow to load chapter/line structures from resources.
- Mixin integration for runtime behavior extensions.

Core entry points:

- Mod bootstrap: [src/main/java/com/hfstudio/bqapi/BQApiMod.java](src/main/java/com/hfstudio/bqapi/BQApiMod.java)
- Public facade: [src/main/java/com/hfstudio/bqapi/BQApi.java](src/main/java/com/hfstudio/bqapi/BQApi.java)
- Builders: [src/main/java/com/hfstudio/bqapi/api/builder](src/main/java/com/hfstudio/bqapi/api/builder)
- Definitions: [src/main/java/com/hfstudio/bqapi/api/definition](src/main/java/com/hfstudio/bqapi/api/definition)
- Importer API: [src/main/java/com/hfstudio/bqapi/api/importer](src/main/java/com/hfstudio/bqapi/api/importer)
- Runtime apply: [src/main/java/com/hfstudio/bqapi/runtime](src/main/java/com/hfstudio/bqapi/runtime)
- Mixin loader/config: [src/main/java/com/hfstudio/bqapi/mixins](src/main/java/com/hfstudio/bqapi/mixins), [src/main/resources/mixins.bqapi.json](src/main/resources/mixins.bqapi.json), [src/main/resources/mixins.bqapi.late.json](src/main/resources/mixins.bqapi.late.json)

## Build

Windows:

```bash
gradlew.bat clean build
```

Linux/macOS:

```bash
./gradlew clean build
```

Tests:

```bash
gradlew.bat test
```

## Artifacts

`build` now keeps the normal mod outputs and also produces an additional API-only jar with classifier `api`.

Expected outputs include:

- Main mod jar (contains mod bootstrap and mixin metadata).
- Dev/sources artifacts from GTNH conventions.
- Additional API jar: `*-api.jar`.

The API jar is intentionally built without:

- Mod bootstrap class: `com.hfstudio.bqapi.BQApiMod`.
- Mixin implementation package: `com.hfstudio.bqapi.mixins`.
- Mixin config resources: `mixins.bqapi.json`, `mixins.bqapi.late.json`.

This keeps runtime initialization and mixin behavior in the normal mod jar unchanged, while exposing a lightweight compile/jar-in-jar API artifact for other mods.

## API Tutorial

### 1. Register Quests in Code

The primary facade is [src/main/java/com/hfstudio/bqapi/BQApi.java](src/main/java/com/hfstudio/bqapi/BQApi.java).

```java
import com.hfstudio.bqapi.BQApi;
import com.hfstudio.bqapi.api.builder.Chapters;
import com.hfstudio.bqapi.api.builder.Quests;
import com.hfstudio.bqapi.api.builder.RewardBuilders;
import com.hfstudio.bqapi.api.builder.TaskBuilders;
import com.hfstudio.bqapi.api.definition.ChapterDefinition;
import com.hfstudio.bqapi.api.definition.QuestDefinition;
import com.hfstudio.bqapi.api.definition.QuestPlacementDefinition;

public class ExampleQuestRegistration {

	public static void registerAll() {
		QuestDefinition starterQuest = Quests.quest("starter_collect_logs")
			.task(TaskBuilders.retrieval("collect_logs")
				.item("minecraft:log", 16, 0)
				.consume(true)
				.build())
			.reward(RewardBuilders.item("starter_reward")
				.item("minecraft:iron_ingot", 4, 0)
				.build())
			.build();

		ChapterDefinition chapter = Chapters.chapter("chapter_getting_started")
			.icon("minecraft:book", 1, 0)
			.quest(new QuestPlacementDefinition(starterQuest, 0, 0, 2, 2))
			.build();

		BQApi.register(chapter);
	}
}
```

Recommended lifecycle usage:

- Register chapters/quests during your mod init phase.
- If your definitions are added after server start, call `BQApi.reinject(server)` manually.

### 1.5 Chapter Ordering

By default, new chapters are placed at the end. To insert a chapter at a specific position,
use `orderAfter()` to specify which existing chapter it should appear after:

```java
import java.util.UUID;
import com.hfstudio.bqapi.BQApi;
import com.hfstudio.bqapi.api.builder.Chapters;

// Order after an existing chapter using its UUID
ChapterDefinition myChapter = Chapters.chapter("my_new_chapter")
	.orderAfter(UUID.fromString("...existing chapter uuid..."))
	.build();

BQApi.register(myChapter);
```

You can also order by chapter ID:

```java
ChapterDefinition myChapter = Chapters.chapter("my_new_chapter")
	.orderAfter("existing_chapter_id")  // The API converts it to UUID automatically
	.build();
```

Keep chapter IDs stable across reloads so their ordering and references remain consistent.

### 2. Import Quest Resources

You can import chapters from resources under your own assets path.

```java
import com.hfstudio.bqapi.BQApi;

public class ExampleImportRegistration {

	public static void registerImported() {
		BQApi.registerImportedFolder("yourmodid", "bqapi/quests");
	}
}
```

This resolves resources from:

- `assets/yourmodid/bqapi/quests`

For advanced control (custom classloader, explicit line directory, UUID behavior), use:

- [src/main/java/com/hfstudio/bqapi/api/importer/ImportedQuestFolders.java](src/main/java/com/hfstudio/bqapi/api/importer/ImportedQuestFolders.java)
- [src/main/java/com/hfstudio/bqapi/api/builder/ImportedChapterBuilder.java](src/main/java/com/hfstudio/bqapi/api/builder/ImportedChapterBuilder.java)

### 3. Runtime Reinjection

World loading is integrated by `MixinSaveLoadHandler`: registered definitions are applied before BetterQuesting reads legacy or per-player progress. `BQApiMod` registers the debug command; there is no second server-started reinjection.

Call `BQApi.reinject(server)` on the active server thread. It runs reload callbacks, captures the registration snapshot, prepares definitions, restores deferred managed progress, and synchronizes only the managed quest and chapter UUIDs. Quest configuration and each player's progress travel together. Players who join later use BetterQuesting's normal login synchronization. The integrated host shares the server database and does not receive a redundant database packet.

An explicit `/bq_admin default load` still replaces the full database. BQAPI inserts its definitions before that command's existing synchronization, without sending another round of packets. Ordinary reinjection does not broadcast settings or clear unrelated quests.

Use explicit `.slot(n)` values for tasks and rewards whose progress must remain stable when definitions are reordered. Automatic slots are assigned only after all explicit slots are reserved.

Registration queries return immutable snapshots. Reload callbacks and patches run outside registry locks; callbacks added during iteration run on a later cycle. Patches must retain the original quest ID and UUID. Use raw task/reward definitions to support BetterQuesting extension factories, or inject custom appliers with the runtime constructors.

Database update notifications still exist in BetterQuesting. A third-party listener must distinguish a UI/data update from a world load and must not clear or recreate quests on every update. BQAPI cannot preserve progress that an external callback explicitly deletes.

If your mod dynamically changes quest definitions after startup, trigger reinjection:

```java
import cpw.mods.fml.common.FMLCommonHandler;
import net.minecraft.server.MinecraftServer;
import com.hfstudio.bqapi.BQApi;

MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
if (server != null) {
	BQApi.reinject(server);
}
```

### 4. Query Existing Chapters and Quests

At any point after registration you can look up definitions by ID or UUID:

```java
import java.util.Optional;
import com.hfstudio.bqapi.BQApi;
import com.hfstudio.bqapi.api.definition.ChapterDefinition;
import com.hfstudio.bqapi.api.definition.QuestDefinition;

// Check existence
boolean exists = BQApi.hasQuest("mymod.chapter1.intro");

// Retrieve by string ID
Optional<QuestDefinition> quest = BQApi.getQuest("mymod.chapter1.intro");
quest.ifPresent(q -> System.out.println("UUID: " + q.getUuid()));

// Retrieve by UUID
Optional<ChapterDefinition> chapter = BQApi.getChapter("mymod.chapter1");
```

### 5. Runtime Quest Patching

Register a patch once at startup (e.g. `FMLPostInitializationEvent`). The patch is
applied on every `reinject` call without modifying the original registered definition:

```java
import com.hfstudio.bqapi.BQApi;
import com.hfstudio.bqapi.api.builder.Quests;
import com.hfstudio.bqapi.api.builder.TaskBuilders;

// Append an extra retrieval task to a quest registered by another mod
BQApi.patchQuest("othermod.some_quest", def ->
	Quests.copyOf(def)
		.task(TaskBuilders.retrieval("extra_diamond")
			.item("minecraft:diamond", 1, 0)
			.build())
		.build());
```

Multiple patches on the same quest are applied in registration order.

### 6. Dynamic Reload via Java SPI (Recommended)

BetterQuestingAPI supports JDK SPI (`ServiceLoader`) for reload callbacks.
This avoids requiring every consumer to bind a dedicated bootstrap class.

#### 6.1 Create a service implementation

```java
import com.hfstudio.bqapi.BQApi;
import com.hfstudio.bqapi.api.QuestReloadService;
import cpw.mods.fml.common.Loader;

public class MyQuestReloadService implements QuestReloadService {

	@Override
	public void reloadQuest() {
		if (Loader.isModLoaded("somemod")) {
			BQApi.register(buildCompatChapter());
		}
	}
}
```

#### 6.2 Add the SPI descriptor file

Create file:

- `src/main/resources/META-INF/services/com.hfstudio.bqapi.api.QuestReloadService`

File content (one implementation class per line):

```text
your.mod.package.MyQuestReloadService
```

`BQApi.reinject(...)` will auto-discover and invoke these services before applying definitions.

#### 6.3 Legacy compatibility: `@QuestReloader`

The annotation-based flow remains supported:

```java
BQApi.registerReloader(MyQuestSetup.class);
```

Use it only when SPI is not convenient.

> **Note**: do **not** call `BQApi.patchQuest()` inside reload callbacks. Patches are
> persistent and accumulate across calls; register them once at startup instead.

Use the classified `api` artifact as your compile/jar-in-jar dependency.

### Gradle Dependency (Template)

Use the published coordinates for this project and request classifier `api`.

```gradle
dependencies {
	// Replace with actual group:artifact:version from published coordinates
	implementation("<group>:<artifact>:<version>:api")
}
```

If your build uses GTNH deobf helpers, apply your usual deobf wrapper around the same `:api` coordinate.

### Forge API Annotation Identity

The project now declares a Forge `@API` annotation on the API package with API id `bqapi|api`:

- Declaration: [src/main/java/com/hfstudio/bqapi/api/package-info.java](src/main/java/com/hfstudio/bqapi/api/package-info.java)
- `owner = bqapi`
- `provides = bqapi|api`

This allows FML to expose the API as an API container for dependency/optional lookups.

### Jar-in-Jar Packaging

When packaging your own mod:

- Include the `api` jar as your embedded library (jar-in-jar), not the full mod bootstrap jar.
- Keep BetterQuestingAPI main mod jar in modpacks/servers that need runtime bootstrap and mixin loading.

Practical rule:

- `*-api.jar`: for compile-time and embedding into dependent mods.
- Main BetterQuestingAPI mod jar: for runtime bootstrap (`@Mod`) and mixins.

## Mixin Safety Notes

The mixin selection logic now supports both "loaded mod" and "declared API" checks:

- Implementation: [src/main/java/com/hfstudio/bqapi/mixins/Mixins.java](src/main/java/com/hfstudio/bqapi/mixins/Mixins.java)
- Condition: `loadedMods.contains(modId) || ModAPIManager.INSTANCE.hasAPI(modId)`

This guarantees API-aware matching in this project. External libraries (for example GTNHLib) will only be API-aware if their own checks also query `ModAPIManager`.

Do not remove these from the normal mod jar:

- [src/main/resources/mixins.bqapi.json](src/main/resources/mixins.bqapi.json)
- [src/main/resources/mixins.bqapi.late.json](src/main/resources/mixins.bqapi.late.json)
- [src/main/java/com/hfstudio/bqapi/mixins/LateMixinLoader.java](src/main/java/com/hfstudio/bqapi/mixins/LateMixinLoader.java)

The API-only jar intentionally excludes them.

## Verification Checklist

After changing build/publishing setup:

1. Run `gradlew.bat clean build`.
2. Confirm `build/libs` contains an additional `*-api.jar`.
3. Inspect normal mod jar and verify `BQApiMod` and mixin resources are still present.
4. Inspect `*-api.jar` and verify `BQApiMod` and mixin package/resources are absent.
5. In a downstream test mod, depend on the `:api` artifact and verify compile + jar-in-jar packaging.

---

## Downstream Migration Guide: From Embedded Sources to API Jar Dependency

This section is for downstream mods that have **copied all BetterQuestingAPI source files directly into their own project**. Follow these steps to delete those redundant copies and instead depend on the official API jar.

### Background: "Jar-in-Jar" and Minecraft 1.7.10

> GTNH Convention on 1.7.10 uses RetroFuturaGradle. The equivalent "embed" mechanism is **Shadow (class merging)**.

Two migration methods are described below — choose the one that fits your setup.

---

### Method A: Shadow Embed (for mods that want no separate BetterQuestingAPI runtime jar)

This method merges the API jar's classes directly into your mod jar. **No separate BetterQuestingAPI main mod jar is needed at runtime.** However, your mod must retain its own mixin injection (see step 4).

#### Step 1: Build and publish the API jar from BetterQuestingAPI

In the BetterQuestingAPI project directory, run:

```
gradlew.bat clean build
```

This produces two files under `build/libs/`:

```
BetterQuestingAPI-<version>.jar       ← Main mod jar (includes BQApiMod + mixins)
BetterQuestingAPI-<version>-api.jar   ← API-only jar (excludes BQApiMod + mixins)
```

To publish via JitPack, update `jitpack.yml` with an explicit `install` step:

```yaml
before_install:
  - ./gradlew setupCIWorkspace
install:
  - ./gradlew clean build
```

JitPack will then publish the `api` classifier artifact. Dependency coordinate:

```
com.github.<username>:BetterQuestingAPI:<version>:api
```

#### Step 2: Delete the embedded BetterQuestingAPI source files

Remove this directory from your downstream project:

```
src/main/java/com/hfstudio/bqapi/
```

> This includes `BQApi.java`, `BQApiMod.java`, `BQApiLangKeys.java`, `Tags.java`, and all
> `api/builder/`, `api/definition/`, `api/importer/`, `runtime/` contents.

Also delete `mixins.bqapi.json` / `mixins.bqapi.late.json` from `resources` if present.

#### Step 3: Enable shadow in `gradle.properties`

Add (or confirm) the following in your `gradle.properties`:

```properties
usesShadowedDependencies = true
```

#### Step 4: Add the dependency in `dependencies.gradle`

```groovy
// Embed API classes into your mod jar (shadow merge)
shadowCompile("com.github.<username>:BetterQuestingAPI:<version>:api") {
    transitive = false
}
// Dev runtime (optional: for local testing with BetterQuesting main mod)
devOnlyNonPublishable("com.github.GTNewHorizons:BetterQuesting:3.x.x-GTNH:dev")
```

#### Step 5: Keep your mod's own mixin injection

The shadow artifact excludes lifecycle mixins. Supply exactly one integration for both world loading and explicit default reloads, using the current `MixinSaveLoadHandler` and `MixinQuestCommandDefaults` implementations:

```java
// Install the current MixinSaveLoadHandler and MixinQuestCommandDefaults integrations.
// Materialize definitions before world progress loading.
// Apply explicit reloads before their existing synchronization.
```

Keep the corresponding enum entry in `Mixins.java` unchanged:

```java
BETTER_QUESTING_RUNTIME(new MixinBuilder("BetterQuesting Runtime API Mixins")
    .addCommonMixins("BetterQuesting.MixinSaveLoadHandler", "BetterQuesting.MixinQuestCommandDefaults")
    .setPhase(Phase.LATE)
    .addRequiredMod(ModList.BetterQuesting)),
```

---

### Method B: External Mod Dependency (recommended for modpack distribution — simplest)

This method embeds no classes. The downstream mod references the API jar at compile time; the BetterQuestingAPI main mod jar is provided by the modpack at runtime, and mixin injection is handled entirely by it.

#### Step 1: Delete the embedded source files (same as Method A Step 2)

Remove `src/main/java/com/hfstudio/bqapi/` entirely.

#### Step 2: Add compile dependency in `dependencies.gradle`

```groovy
// Compile-only reference (main mod jar provided by modpack at runtime)
compileOnly("com.github.<username>:BetterQuestingAPI:<version>:api") {
    transitive = false
}
// Dev environment runtime (includes main mod jar — provides mixin + init logic)
devOnlyNonPublishable("com.github.<username>:BetterQuestingAPI:<version>")
devOnlyNonPublishable("com.github.GTNewHorizons:BetterQuesting:3.x.x-GTNH:dev")
```

#### Step 3: Delete the redundant mixin from your mod

Because the BetterQuestingAPI main mod jar at runtime already includes the complete `MixinQuestCommandDefaults` (injecting both `load` and `loadLegacy`), your mod **no longer needs its own copy**.

Delete:
```
src/main/java/<yourpkg>/mixins/late/BetterQuesting/MixinQuestCommandDefaults.java
```

And remove the corresponding enum entry from `Mixins.java`:

```java
// Remove this entire block
BETTER_QUESTING_RUNTIME(new MixinBuilder("BetterQuesting Runtime API Mixins")
    .addCommonMixins("BetterQuesting.MixinSaveLoadHandler", "BetterQuesting.MixinQuestCommandDefaults")
    .setPhase(Phase.LATE)
    .addRequiredMod(ModList.BetterQuesting)),
```

#### Step 4: Declare dependency in the `@Mod` annotation

Add `bqapi` to the `dependencies` field of your mod's main class:

```java
@Mod(
    modid = "yourmodid",
    // ...
    dependencies = "required-after:bqapi"
)
```

This ensures FML loads BetterQuestingAPI before your mod.

---

### API Initialization Notes

The main mod owns the lifecycle integration and requires BetterQuesting. Embedded API users must supply the equivalent lifecycle hooks exactly once:

| Hook | Runtime call |
|------|--------------|
| Before world database loading | `BQApi.getRuntime().beginWorldLoad(server)` |
| Immediately before world progress loading | `BQApi.getRuntime().beforeWorldProgress(server)` |
| After an explicit database reload, before its normal sync | `BQApi.getRuntime().afterDatabaseLoad(server)` |
| After world database unload | `BQApi.getRuntime().unload()` |

These hooks run on the server thread. `BQReinjector.reinject(server)` remains a compatibility alias for `BQApi.reinject(server)`. When using the main mod, downstream mods only register definitions and callbacks.

### Multi-Mod Coexistence Compatibility

This section answers the question: **what happens when the main BetterQuestingAPI jar is present in the modpack AND one or more mods have shadow-embedded the api jar? Or when multiple mods all shadow-embed the api jar?**

#### Class Loading (No Conflict)

Minecraft 1.7.10 uses a single `LaunchClassLoader` for all mods. When multiple jars contain the same class name (e.g. `com.hfstudio.bqapi.BQApi`), the classloader loads **whichever copy it finds first** and ignores the rest.

This means:
- There is always exactly **one** `BQApi` class and one `BQApi.REGISTRY` static field in the JVM.
- Registrations from all mods accumulate in the same registry — correct behavior. ✅
- `BQApiMod` only exists in the main mod jar (shadow embed excludes it), so it initialises exactly once. ✅

> **Version mismatch risk**: If mod A shadows version 1.0.0 and mod B shadows version 1.1.0, whichever loads first wins. The other mod silently uses the older API. Always keep all shadow copies on the same version.

#### Duplicate Lifecycle Integrations

Use one lifecycle integration. Multiple copies of the same redirect can conflict during Mixin application, and sequential duplicate callbacks can repeat external side effects.

Reentrant reinjection requests are folded into the active cycle. Completed calls are not suppressed by elapsed time: a second real database reload must always be applied. Reload callbacks must be idempotent. Classloader deduplication does not guarantee compatibility between different embedded API versions.

#### Recommendation

For the cleanest setup in a modpack that ships BetterQuestingAPI as a standalone mod:
- Use **Method B (external mod dependency)** for all downstream mods.
- Remove downstream lifecycle mixin copies; the main mod jar handles world loading and default reloads.
- Use the main mod as the single owner of each loading phase.
