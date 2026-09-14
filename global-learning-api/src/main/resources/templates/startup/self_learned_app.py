from pathlib import Path

import pandas as pd

from config import MyAppConfig, MyAppInputConfig, MyAppOutputConfig
from pyfedappwrap.learning.app_types import BaseSelfLearnedApp


class {name}(BaseSelfLearnedApp[MyAppConfig, MyAppInputConfig, MyAppOutputConfig]):

    def __init__(self):
        super().__init__()

    def run_algorithm(self, data: MyAppInputConfig) -> MyAppOutputConfig:
        # TODO REPLACE with your algorithm logic
        self.logger.info(f"Run with")
        df: pd.DataFrame = data.features

        self.send_metric("rows", 0, int(len(df)))
        self.send_metric("cols", 0, int(len(df.columns)))
        self.send_metric("missing_cells", 1, int(df.isna().sum().sum()))

        out = Path("evaluation.txt")
        out.write_text(
           "result",
            encoding="utf-8",
        )

        return MyAppOutputConfig(report=out)
