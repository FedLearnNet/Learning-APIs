from pathlib import Path

import pandas as pd

from config import MyAppConfig, MyAppOutputConfig
from pyfedappwrap.types.export.base_exporter import BaseExporterAPP


class MyTest(BaseExporterAPP[MyAppConfig, MyAppOutputConfig]):

    def __init__(self):
        super().__init__()

    def export(self, df: pd.DataFrame) -> MyAppOutputConfig:
        self.logger.info("exporting")

        self.send_metric("rows", float(len(df)), 0.0)
        self.send_metric("cols", float(len(df.columns)), 0.0)
        self.send_metric("missing_cells", float(df.isna().sum().sum()), 0.0)

        out = Path("evaluation.txt")
        out.write_text(
            f"rows={len(df)}\ncols={len(df.columns)}\nmissing_cells={int(df.isna().sum().sum())}\n",
            encoding="utf-8",
        )

        return MyAppOutputConfig(report=out)
