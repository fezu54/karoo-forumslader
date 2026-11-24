# `karoo-ext` (`1.1.9`) Quick Reference

Verified from existing implementations in `app/src/main/kotlin/.../extension/`, `ForumsladerDataFieldsAdapter.kt`, and `app/src/main/res/xml/extension_info.xml`. **Do not unpack `karoo-ext.aar`.**

## 1. Extension Entry Point (`KarooExtension`)

Subclass `KarooExtension(extension = "karoo-forumslader", version = BuildConfig.VERSION_NAME)` (`ForumsladerExtension.kt`):
- `override val types: List<DataTypeImpl>` — Register `ForumsladerDataType` (numeric/custom text) and `ForumsladerGraphicalDataType` (`RemoteViews`).
- `override fun startScan(emitter: Emitter<Device>)` — Emit `Device(extension, uid, dataTypes, displayName)` via `emitter.onNext(...)`; clean up scan/coroutines in `emitter.setCancellable { ... }`.
- `override fun connectDevice(uid: String, emitter: Emitter<DeviceEvent>)` — Emit `OnConnectionStatus(ConnectionStatus)` and `OnDataPoint(DataPoint(dataTypeId, values, sourceId))`; tear down in `emitter.setCancellable { ... }`.
- `override fun startFit(emitter: Emitter<FitEffect>)` — Store/clear FIT emitter and cancel via `emitter.setCancellable { ... }`.

Manifest metadata lives in `app/src/main/res/xml/extension_info.xml`: `<ExtensionInfo id="karoo-forumslader" scansDevices="true" fitFile="true">` containing `<DataType typeId="..." displayName="..." description="..." icon="..." graphical="true|false" />`.

## 2. `KarooSystemService` Consumers & Dispatch

Create with `KarooSystemService(context.applicationContext)` (`ForumsladerKarooAdapter.kt`, `ForumsladerDataType.kt`, `ForumsladerGraphicalDataType.kt`):
- Lifecycle: `karooSystem.connect { connected -> ... }` and `karooSystem.disconnect()`. Store listener IDs returned by `addConsumer(...)` and unregister each via `karooSystem.removeConsumer(id)` inside `emitter.setCancellable`.
- Stream subscription: `karooSystem.addConsumer(OnStreamState.StartStreaming(dataTypeId)) { event: OnStreamState -> ... }`
  - `event.state` is `StreamState.Streaming` (`state.dataPoint.values`, `state.dataPoint.singleValue`), `StreamState.Searching`, `StreamState.NotAvailable`, or `StreamState.Idle`.
  - Standard IDs used here: `DataType.Type.DISTANCE_TO_DESTINATION`, `DataType.Type.ELEVATION_REMAINING`, `DataType.Type.ELEVATION_GRADE`, `DataType.Type.SPEED`, `DataType.Type.DISTANCE`, `DataType.Type.POWER`, `DataType.Type.ENERGY_OUTPUT`, and `DataType.dataTypeId(extension, typeId)` (e.g., `DataType.dataTypeId("karoo-headwind", "headwindSpeed")`).
  - Standard fields used here: `DataType.Field.SINGLE`, `DataType.Field.DISTANCE_TO_DESTINATION`, `DataType.Field.ASCENT_REMAINING`, `DataType.Field.ELEVATION_GRADE`.
- Other consumers:
  - `OnNavigationState` -> `OnNavigationState.NavigationState.NavigatingRoute` (`routeDistance`, `climbs` with `startDistance`, `length`, `grade`) / `OnNavigationState.NavigationState.Idle`.
  - `RideState` -> `RideState.Idle`, `RideState.Recording`, `RideState.Paused`.
  - `UserProfile` -> `profile.preferredUnit.distance == UserProfile.PreferredUnit.UnitType.IMPERIAL`.
- Alerts & notifications via `karooSystem.dispatch(...)`:
  - `InRideAlert(id, icon, title, detail, autoDismissMs, backgroundColor, textColor)`
  - `SystemNotification(id, header, message, subText, style = SystemNotification.Style.EVENT)`

## 3. Data Fields (`DataTypeImpl` & `ViewEmitter`)

Override `startView(context: Context, config: ViewConfig, emitter: ViewEmitter)`:
- `ViewConfig` exposes `preview`, `viewSize` (`Pair<Int, Int>`), `gridSize` (`Pair<Int, Int>`), `textSize`.
- Numeric formatted fields (`ForumsladerDataType`): emit `emitter.onNext(UpdateNumericConfig(formatDataTypeId ?: dataTypeId))`.
- Custom text fields (`ForumsladerDataType`): emit `emitter.onNext(ShowCustomStreamState(text, color = colorIntOrNull))`.
- Graphical fields (`ForumsladerGraphicalDataType`): build `RemoteViews` and call `emitter.updateView(views)` (throttle updates ~1 Hz to save Karoo battery).

## 4. FIT Recording (`FitEffect`)

Used in `ForumsladerFitRecorder.kt`:
- Define fields: `DeveloperField(0, FIT_BASE_TYPE_FLOAT32, "Battery Voltage", "V")` (positional: `fieldDefinitionNumber`, `fitBaseType: Short = 136`, `fieldName`, `units`).
- Emit record/session values: `WriteToRecordMesg(listOf(FieldValue(field, doubleValue)))` and `WriteToSessionMesg(sessionValues)`.

## 5. Unit Testing Libraries

Already declared in `gradle/libs.versions.toml`: `karoo-ext-testing`, `karoo-ext-testing-robolectric`, `robolectric`, `mockk`, `kotest`, `kotlinx-coroutines-test`. See `app/src/test/kotlin/.../extension/` for test fakes and `ViewEmitter`/`Emitter` patterns.
