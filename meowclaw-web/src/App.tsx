import { useEffect } from "react";
import { InitPage } from "@/pages/init";
import { authService } from "@/services/auth";
import { useAppStore } from "@/stores";
import { RoutesView } from "@/routes";
import { BrowserRouter } from "react-router-dom";
import { ThemeProvider } from "next-themes";
import { Toaster } from "@/components/ui/sonner";

function App() {
  const { isInitialized, setInitialized } = useAppStore();

  useEffect(() => {
    if (isInitialized === null) {
      authService
        .getInitStatus()
        .then((response) => {
          setInitialized(
            response.code === 200 && response.data
              ? response.data.initialized
              : false,
          );
        })
        .catch(() => setInitialized(false));
    }
  }, [isInitialized, setInitialized]);

  if (isInitialized === null) {
    return null;
  }

  return (
    <ThemeProvider
      attribute="class"
      defaultTheme="system"
      enableSystem
      disableTransitionOnChange
    >
      <BrowserRouter>
        {!isInitialized ? <InitPage /> : <RoutesView />}
      </BrowserRouter>
      <Toaster />
    </ThemeProvider>
  );
}

export default App;
