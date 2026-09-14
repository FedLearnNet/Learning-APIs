# MyAPP — BaseApp Template

This repository is a **template** for implementing an app on top of the `BaseApp[Config, Input, Output]` interface.
Your app defines:
- **Hyperparameters** (`self.config`) via a **Pydantic** model (`MyAppConfig`)
- **Input schema** via a **Pydantic** model (`MyAppInputConfig`) — including optional file inputs
- **Output schema** via a **Pydantic** model (`MyAppOutputConfig`) — including structured values + file artifacts

---

# Core concepts

The framework handles:
- Loading/validating hyperparameters into self.config (Pydantic)
- Parsing/validating input into MyAppInputConfig (Pydantic)
- Converting output (MyAppOutputConfig) into final artifacts:
- Path outputs get collected and uploaded
- pandas.DataFrame outputs get serialized and uploaded
- Primitive fields stay in the JSON result

⸻

##  Schemas (Pydantic)

### Hyperparameters (self.config)
- self.config contains your app’s hyperparameters (Pydantic object).
- Use it in run() to control behavior (epochs, thresholds, model choice, etc.).

Example pattern:
```python 
# inside run()
lr = self.config.learning_rate
epochs = self.config.epochs
```

### Input
- Input is a Pydantic model provided to run(data).
- Files are normalized by the framework:
  - If possible, tabular files are parsed into pandas DataFrames
  - Otherwise they remain file-like / path-like depending on your framework integration
- For testing: 
  - Use the data data folder to store sample input files

Typical usage:
```python 
def run(self, data: MyAppInputConfig) -> MyAppOutputConfig:
    self.logger.info(f"Received input: {{data}}")
    # Example: data.table could be a pandas DataFrame
    # Example: data.image could be a Path
```

### Output
- Output is a Pydantic model you return from run().
- Special handling:
  - Path fields → treated as files, uploaded as file
  - pandas.DataFrame fields → serialized (csv/parquet/etc.) and uploaded
  - other fields → returned as JSON metadata
- Returning files: ```output_file=Path("./test_image.png")```
  - Ensure the file exists before returning
- Returning pandas DataFrames
  - If your output model contains a pandas.DataFrame, it will be serialized and uploaded automatically.
  - Keep schemas stable (column names/types)
  -	Avoid huge frames unless necessary (or write chunked artifacts)

## Logging and metrics

### Logging
Use the built-in logger:
```python 
self.logger.info("Training")
self.logger.warning("Training completed")
self.logger.error("Training failed")
```
### Metrics streaming
Use send_metric(name, step, value) to emit live metrics:
```python 
for i in range(10):
    self.send_metric("accuracy", i, i)
    self.send_metric("loss", i, i)
```

# Project structure

Adjust to your repo layout.

```
├─ app.py             # Your app implementation
├─ config.py          # Pydantic schemas
├─ main.py            # Startup script
├─ requirements.txt   # To install dependencies
├─ data/              # Sample input data for testing
├─ app.yml            # App metadata for the runtime
├─ .env               # Environment variables
├─ Dockerfile         # Container setup
└─ README.md
```

# Examples
Your app class implements the BaseApp contract:
```python 
{example}
```

{#if type == "DATA_TRANSFORMATION"}
# Data Transform
Implement a data transformation app that processes input data and produces transformed output.
You can use single value transformations, which can be executed on each row of a dataset, or multi value transformations, which can operate on the entire dataset at once.
You can implement also row transformations, which take a dictionary as input and return a transformed dictionary.
## Single Value Transformation
Function needs to take a single input value and return a transformed output value.
```python 
def transform(self, value: str) -> str:
    pass
```
## Row Transformation
Function needs to take a dict and return a transformed dict.
It needs to handle the value with static keys.
```python 
def transform(self, value: dict) -> dict:
    pass
```
{/if}
{#if type == "EXTRACTOR"}
# Database Adopter
Has no input, can be used to get data from a database and output it in a structured format.
{/if}