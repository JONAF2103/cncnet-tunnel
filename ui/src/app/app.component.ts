import {Component, ComponentFactoryResolver, ComponentRef, OnInit, ViewChild, ViewContainerRef} from '@angular/core';
import {RouterService} from './services/router.service';
import {LoginComponent} from './components/login/login.component';

@Component({
  selector: 'app-root',
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.scss']
})
export class AppComponent implements OnInit {
  @ViewChild('routerHost', { static: true, read: ViewContainerRef })
  routerHost: ViewContainerRef;

  constructor(private routerService: RouterService,
              private resolver: ComponentFactoryResolver) {
    routerService.currentRoute.subscribe(currentRoute => {
      this.showComponent(currentRoute);
    });
  }

  updateTitle(title): void {
    document.title = title;
  }

  private showComponent(currentRoute: any) {
    if (this.routerHost) {
      this.routerHost.clear();
      const factory = this.resolver.resolveComponentFactory(currentRoute);
      const component: ComponentRef<any> = this.routerHost.createComponent(factory);
      this.updateTitle(component.instance.title);
    }
  }

  ngOnInit(): void {
    this.showComponent(LoginComponent);
  }

}
