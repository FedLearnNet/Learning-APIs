from __future__ import annotations

from typing import Any, Optional

import numpy as np

from pyfedappwrap.engine.federated import AppAggregator, FLNetMessageMetaDTO


class MAggregator(AppAggregator):
    """
    Simple aggregation class that averages the payloads.
    """

    def aggregate(
            self,
            data: list[Any],
            n_clients: int,
            meta: Optional[FLNetMessageMetaDTO] = None,
    ) -> Any:
        if not data:
            raise ValueError("Cannot aggregate an empty payload list.")

        # implement your aggregation logic here

        return np.mean(np.stack(data), axis=0)