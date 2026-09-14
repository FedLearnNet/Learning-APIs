package bio.cosy.feddb.core.api.app.config;

    public enum ToolConfigModeType {
    TRAINING, PREDICTION, BOTH;


        public static boolean isTraining(ToolConfigModeType mode) {
            //Default mode is both
            if (mode == null) {
                return true;
            }
            return mode == TRAINING || mode == BOTH;
        }

        public static boolean isPrediction(ToolConfigModeType mode) {
            //Default mode is both
            if (mode == null) {
                return true;
            }
            return mode == PREDICTION || mode == BOTH;
        }
}
