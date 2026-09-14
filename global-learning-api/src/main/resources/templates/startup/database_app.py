from pathlib import Path
import pandas as pd

from config import MyAppConfig
from pyfedappwrap.types.databaseadopter.base_importer import BaseDatabaseAdopterAPP


class {name}(BaseDatabaseAdopterAPP[MyAppConfig]):

    def run_adopter(self) -> pd.DataFrame | Path:
        # TODO REPLACE with your database adoption logic
        return pd.DataFrame({
            "col1": [1, 2, 3],
            "col2": ["a", "b", "c"]
        })

    def __init__(self):
        super().__init__()
